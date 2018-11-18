package PacketAssembler//note

import chisel3._
import chisel3.util._
import CRC._
import Whitening._
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.subsystem.BaseSubsystem


class PAInputBundle extends Bundle {
	val trigger = Output(Bool())
	val data = Output(UInt(8.W))
	//val crc_seed = Output(UInt(24.W))
	//val white_seed = Output(UInt(7.W))

	override def cloneType: this.type = PAInputBundle().asInstanceOf[this.type]
}

object PAInputBundle {
  def apply(): PAInputBundle = new PAInputBundle
}

class PAOutputBundle extends Bundle {
	val data = Output(UInt(1.W))
	val done = Output(Bool())

	override def cloneType: this.type = PAOutputBundle().asInstanceOf[this.type]
}

object PAOutputBundle {
  def apply(): PAOutputBundle = new PAOutputBundle
}


class PacketAssemblerIO extends Bundle {
	val in = Flipped(Decoupled(PAInputBundle()))
	val out = Decoupled(PAOutputBundle())

	override def cloneType: this.type = PacketAssemblerIO().asInstanceOf[this.type]
}

object PacketAssemblerIO {
  def apply(): PacketAssemblerIO = new PacketAssemblerIO
}	

trait HasPeripheryPA extends BaseSubsystem {
  // instantiate cordic chain
  val paChain = LazyModule(new PAThing)
  // connect memory interfaces to pbus
  pbus.toVariableWidthSlave(Some("paWrite")) { paChain.writeQueue.mem.get }
  pbus.toVariableWidthSlave(Some("paRead")) { paChain.readQueue.mem.get }
}

class PacketAssembler extends Module {

	def stateUpdate(currentState: UInt, nextState: UInt, length: UInt, counter: UInt, counterByte: UInt, condition: Bool)= {
		val stateOut = Wire(UInt(3.W))
		val counterOut = Wire(UInt(8.W))
		val counterByteOut = Wire(UInt(3.W))
		counterOut := counter
		counterByteOut := counterByte
		
		when(counter === length - 1.U && counterByte === 7.U && condition){
			stateOut := nextState
			counterOut := 0.U
			counterByteOut := 0.U
		}.otherwise{
			stateOut := currentState
			when(condition){
				when(counterByte === 7.U){
					counterOut := counter + 1.U
					counterByteOut := 0.U
				}.otherwise{
					counterByteOut := counterByte + 1.U					
				}
			}
		}				
		(stateOut, counterOut, counterByteOut)
	}

    val io = IO(new PacketAssemblerIO)

	//state parameter
	val idle :: preamble :: aa :: pdu_header :: pdu_payload :: crc :: Nil = Enum(6)
	val state = RegInit(idle)

	val counter = RegInit(0.U(8.W)) //counter for bytes in packet
	val counter_byte = RegInit(0.U(3.W)) //counter for bits in bytes

	val pdu_length = RegInit(0.U(8.W))

	//Preamble
	val preamble0 = "b10101010".U //flipped preamble; start with least significant bit
	val preamble1 = "b01010101".U

	//Handshake parameters
	val in_ready = RegInit(false.B)
	val out_valid = RegInit(false.B)
	val in_fire = io.in.ready && io.in.valid
	val out_fire = io.out.ready && io.out.valid

	//data registers
	val data = RegInit(0.U(8.W))

	//CRC
	val crc_reset = io.in.bits.trigger && io.in.valid
	val crc_data = Wire(UInt(1.W))
	val crc_valid = Wire(Bool())
	val crc_result = Wire(UInt(24.W))
	val crc_seed = Wire(UInt(24.W))

	//whitening
	val white_reset = io.in.bits.trigger && io.in.valid
	val white_data = Wire(UInt(1.W))
	val white_valid = Wire(Bool())	
	val white_result = Wire(UInt(1.W))
	val white_seed = Wire(UInt(7.W))
	
	//hardcode seed initiation 
	crc_seed := "b010101010101010101010101".U
	white_seed := "b1100101".U			

	//decouple assignments
	io.in.ready := in_ready
	io.out.valid := out_valid

	//output bits
	when(state === idle){
		io.out.bits.data := 0.U
	}.otherwise{
		when(state === pdu_header || state === pdu_payload || state === crc){
			io.out.bits.data := white_result
		}.otherwise{//PREAMBLE, aa
			io.out.bits.data := data(counter_byte)
		}
	}
	
	when(state === crc && counter === 2.U && counter_byte === 7.U && out_fire){//end of the packet
		io.out.bits.done := true.B	
	}.otherwise{
		io.out.bits.done := false.B
	}
	
	//State Transition with counter updates
	when(state === idle){
		when(io.in.bits.trigger === true.B && io.in.valid){
			state := preamble
			counter := 0.U
			counter_byte := 0.U
		}.otherwise{
			state := idle
		}
	}.elsewhen(state === preamble){
		val (stateOut, counterOut, counterByteOut) = stateUpdate(preamble, aa, 1.U, counter, counter_byte, out_fire)
		state := stateOut
		counter := counterOut
		counter_byte := counterByteOut		
	}.elsewhen(state === aa){
		val (stateOut, counterOut, counterByteOut) = stateUpdate(aa, pdu_header, 4.U, counter, counter_byte, out_fire)
		state := stateOut
		counter := counterOut
		counter_byte := counterByteOut			
	}.elsewhen(state === pdu_header){
		val (stateOut, counterOut, counterByteOut) = stateUpdate(pdu_header, pdu_payload, 2.U, counter, counter_byte, out_fire)
		state := stateOut
		counter := counterOut
		counter_byte := counterByteOut					
	}.elsewhen(state === pdu_payload){
		val (stateOut, counterOut, counterByteOut) = stateUpdate(pdu_payload, crc, pdu_length, counter, counter_byte, out_fire)
		state := stateOut
		counter := counterOut
		counter_byte := counterByteOut			
	}.elsewhen(state === crc){
		val (stateOut, counterOut, counterByteOut) = stateUpdate(crc, idle, 3.U, counter, counter_byte, out_fire)
		state := stateOut
		counter := counterOut
		counter_byte := counterByteOut		
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
		when(state === pdu_payload && counter === pdu_length-1.U && counter_byte === 7.U && out_fire){
			in_ready := false.B//special case at the end of PAYLOAD		
		}.elsewhen(counter_byte === 7.U && out_fire){
			in_ready := true.B
		}.elsewhen(in_fire === 1.U){
			in_ready := false.B		
		}.otherwise{
			
		}		
	}.otherwise{//IDLE, PREAMBLE, CRC
		when(state === preamble && counter === 0.U && counter_byte === 7.U && out_fire){
			in_ready := true.B//special case at the end of PREAMBLE: aa starts with ready
		}.elsewhen(state === idle){
			in_ready := true.B
		}.otherwise{
			in_ready := false.B
		}
	}

	//output valid
	when(state === idle){
		out_valid := false.B
	}.elsewhen(state === preamble){
		when(counter === 0.U && counter_byte === 7.U && out_fire){
			out_valid := false.B//special case at the end of PREAMBLE: aa starts with invalid
		}.elsewhen(io.in.valid){
			out_valid := true.B
		}
	}.elsewhen(state === crc){
		when(counter === 2.U && counter_byte === 7.U && out_fire){
			out_valid := false.B//special case at the end of CRC		
		}.otherwise{
			out_valid := true.B			
		}
	}.otherwise{//aa, pdu_header, pdu_payload
		when(counter_byte === 7.U && out_fire){
			out_valid := false.B			
		}.elsewhen(in_fire === 1.U){
			out_valid := true.B				
		}
	}

	//data
	when(state === aa || state === pdu_header || state === pdu_payload){
		when(in_fire){
			data := io.in.bits.data			
		}.otherwise{
			data := data
		}
	}.elsewhen(state === preamble){
		when(io.in.valid){
			when(io.in.bits.data(0) === 0.U){//note: problems when not firing
				data := preamble0
			}.otherwise{
				data := preamble1
			}
		}.otherwise{
			data := data
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
		crc_valid := out_fire
	}.otherwise{
		crc_data := 0.U
		crc_valid := false.B
	}

	//Set Whitening Parameters
	when(state === pdu_header || state === pdu_payload || state === crc){	
		white_data  := data(counter_byte)//note
		white_valid := out_fire
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
