package PacketAssembler//note

import chisel3._
import chisel3.util._
import CRC._
import Whitening._


class PABundle extends Bundle {
	val trigger = Input(Bool())
	val data = Flipped(Decoupled(UInt(8.W)))
	val crc_seed = Input(UInt(24.W))
	val white_seed = Input(UInt(7.W))
	val done = Output(Bool())

	override def cloneType: this.type = PABundle.asInstanceOf[this.type]
}
object PABundle {
  def apply = new PABundle
}

class PacketAssemblerIO extends Bundle {
	val in = new PABundle
	val out = Decoupled(UInt(1.W))
}

trait HasPeripheryPA extends BaseSubsystem {
  // instantiate cordic chain
  val paChain = LazyModule(new CordicThing(FixedCordicParams(8, 10)))
  // connect memory interfaces to pbus
  pbus.toVariableWidthSlave(Some("paWrite")) { paChain.writeQueue.mem.get }
  pbus.toVariableWidthSlave(Some("paRead")) { paChain.readQueue.mem.get }
}

class PacketAssembler extends Module {
    val io = IO(new PacketAssemblerIO)

	//state parameter
	val idle :: preamble :: aa :: pdu_header :: pdu_payload :: crc :: Nil = Enum(6)
	val state = RegInit(idle)

	val counter = RegInit(0.U(8.W)) //counter for bytes in packet
	val counter_byte = RegInit(0.U(3.W)) //counter for bits in bytes

	val pdu_length = RegInit(0.U(8.W))

	//Preamble
	val preamble0 = "b10101010".U
	val preamble1 = "b01010101".U

	//Handshake parameters
	val in_ready = RegInit(false.B)
	val out_valid = RegInit(false.B)

	//data registers
	val data = RegInit(0.U(8.W))

	//CRC
	val crc_reset = io.in.trigger
	val crc_data = Wire(UInt(1.W))
	val crc_valid = Wire(Bool())
	val crc_result = Wire(UInt(24.W))
	val crc_seed = io.in.crc_seed

	//whitening
	val white_reset = io.in.trigger
	val white_data = Wire(UInt(1.W))
	val white_valid = Wire(Bool())	
	val white_result = Wire(UInt(1.W))
	val white_seed = io.in.white_seed			

	//decouple assignments
	io.in.data.ready := in_ready
	io.out.valid := out_valid

	//output bits
	when(state === idle){
		io.out.bits := 0.U
	}.otherwise{
		when(state === pdu_header || state === pdu_payload || state === crc){
			io.out.bits := white_result
		}.otherwise{//PREAMBLE, aa
			io.out.bits := data(counter_byte)
		}
	}

	when(state === crc && counter === 2.U && counter_byte === 7.U && io.out.fire() === true.B){//end of the packet
		io.in.done := true.B	
	}.otherwise{
		io.in.done := false.B
	}

	//State Transition with counter updates
	when(state === idle){
		when(io.in.trigger === true.B){
			state := preamble
			counter := 0.U
			counter_byte := 0.U
		}.otherwise{
			state := idle
		}
	}.elsewhen(state === preamble){
		when(counter === 0.U && counter_byte === 7.U && io.out.fire() === true.B){//finish transmitting 1 byte of preamble
			state := aa
			counter := 0.U
			counter_byte := 0.U
		}.otherwise{
			state := preamble
			when(io.out.fire() === true.B){
				when(counter_byte === 7.U){
					counter := counter + 1.U
					counter_byte := 0.U
				}.otherwise{
					counter_byte := counter_byte + 1.U					
				}
			}
		}		
	}.elsewhen(state === aa){
		when(counter === 3.U && counter_byte === 7.U && io.out.fire() === true.B){//finish transmitting 4 bytes of access address
			state := pdu_header
			counter := 0.U
			counter_byte := 0.U
		}.otherwise{
			state := aa
			when(io.out.fire() === true.B){
				when(counter_byte === 7.U){
					counter := counter + 1.U
					counter_byte := 0.U
				}.otherwise{
					counter_byte := counter_byte + 1.U					
				}
			}				
		}			
	}.elsewhen(state === pdu_header){
		when(counter === 1.U && counter_byte === 7.U && io.out.fire() === true.B){//finish transmitting 2 bytes of pdu header
			state := pdu_payload
			counter := 0.U
			counter_byte := 0.U
		}.otherwise{
			state := pdu_header
			when(io.out.fire() === true.B){
				when(counter_byte === 7.U){
					counter := counter + 1.U
					counter_byte := 0.U
				}.otherwise{
					counter_byte := counter_byte + 1.U					
				}
			}
		}			
	}.elsewhen(state === pdu_payload){
		when(counter === pdu_length - 1.U && counter_byte === 7.U && io.out.fire() === true.B){//finish transmitting pdu payload
			state := crc
			counter := 0.U
			counter_byte := 0.U
		}.otherwise{
			state := pdu_payload
			when(io.out.fire() === true.B){
				when(counter_byte === 7.U){
					counter := counter + 1.U
					counter_byte := 0.U
				}.otherwise{
					counter_byte := counter_byte + 1.U					
				}
			}
		}			
	}.elsewhen(state === crc){
		when(counter === 2.U && counter_byte === 7.U && io.out.fire() === true.B){//finish transmitting crc
			state := idle
			counter := 0.U
			counter_byte := 0.U
		}.otherwise{
			state := crc
			when(io.out.fire() === true.B){
				when(counter_byte === 7.U){
					counter := counter + 1.U
					counter_byte := 0.U
				}.otherwise{
					counter_byte := counter_byte + 1.U					
				}
			}
		}		
	}.otherwise{
		state := idle//error
	}


	//PDU_Length
	when(state === pdu_header && counter === 1.U){
		pdu_length := data
	}.otherwise{
		//do nothing: registers preserve value//note
	}

	//in_ready //note:check corner cases
	when(state === aa || state === pdu_header || state === pdu_payload){
		when(state === pdu_payload && counter === pdu_length-1.U && counter_byte === 7.U && io.out.fire() === true.B){
			in_ready := false.B//special case at the end of PAYLOAD		
		}.elsewhen(counter_byte === 7.U && io.out.fire() === true.B){
			in_ready := true.B
		}.elsewhen(io.in.data.fire() === true.B){
			in_ready := false.B		
		}.otherwise{
			//do nothing
		}		
	}.otherwise{//IDLE, PREAMBLE, CRC
		when(state === preamble && counter === 0.U && counter_byte === 7.U && io.out.fire() === true.B){
			in_ready := true.B//special case at the end of PREAMBLE: aa starts with ready
		}.otherwise{
			in_ready := false.B
		}
	}

	//output valid
	when(state === idle){
		out_valid := false.B
	}.elsewhen(state === preamble){
		when(counter === 0.U && counter_byte === 7.U && io.out.fire() === true.B){
			out_valid := false.B//special case at the end of PREAMBLE: aa starts with invalid
		}.otherwise{
			out_valid := true.B
		}
	}.elsewhen(state === crc){
		when(counter === 2.U && counter_byte === 7.U && io.out.fire() === true.B){
			out_valid := false.B//special case at the end of CRC		
		}.otherwise{
			out_valid := true.B			
		}
	}.otherwise{//aa, pdu_header, pdu_payload
		when(counter_byte === 7.U && io.out.fire() === true.B){
			out_valid := false.B			
		}.elsewhen(io.in.data.fire() === true.B){
			out_valid := true.B				
		}
	}

	//data
	when(state === aa || state === pdu_header || state === pdu_payload){
		when(io.in.data.fire()){
			data := io.in.data.bits			
		}.otherwise{
			data := data
		}
	}.elsewhen(state === preamble){
		when(io.in.data.bits(0) === 0.U){//note: problems when not firing
			data := preamble0
		}.otherwise{
			data := preamble1
		}
	}.elsewhen(state === crc){
		when(counter === 0.U){
			data := crc_result(7,0)
		}.elsewhen(counter === 1.U){
			data := crc_result(15,8)				
		}.elsewhen(counter === 2.U){
			data := crc_result(23,16)							
		}.otherwise{
			data := crc_result(7,0)//error
		}
	}.otherwise{//IDLE
		data := 0.U//or preserve
	}

	//Set CRC Parameters 
	when(state === pdu_header || state === pdu_payload){
		crc_data := data(counter_byte)
		crc_valid := io.out.fire()
	}.otherwise{
		crc_data := 0.U
		crc_valid := false.B
	}

	//Set Whitening Parameters
	when(state === pdu_header || state === pdu_payload || state === crc){	
		white_data  := data(counter_byte)//note
		white_valid := io.out.fire()
	}.otherwise{
		white_data  := 0.U
		white_valid := false.B
	}


	//Instantiate CRC Module
	val serial_crc = Module(new Serial_CRC)

	serial_crc.io.init := crc_reset
	serial_crc.io.operand.bits := crc_data
	serial_crc.io.operand.valid := crc_valid
	crc_result := serial_crc.io.result.bits
	serial_crc.io.result.ready := true.B
	serial_crc.io.seed := crc_seed

	//Instantiate Whitening Module
	val white = Module(new Whitening)

	white.io.init := white_reset
	white.io.operand.bits := white_data
	white.io.operand.valid := white_valid
	white_result := white.io.result.bits
	white.io.result.ready := true.B
	white.io.seed := white_seed
}
