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


class PacketAssembler extends Module {
    val io = IO(PacketAssemblerIO)

	//state parameter
	val idle :: preamble :: aa :: pdu_header :: pdu_payload :: crc :: Nil = Enum(6)
	val state = RegInit(idle)

	val counter_w = Wire(UInt(8.W))//at most 255 for PDU
	val counter_r = RegInit(0.U(8.W))

	val counter_byte_w = Wire(UInt(3.W))//byte in bit out
	val counter_byte_r = RegInit(0.U(8.W))	

	val pdu_length = RegInit(0.U(8.W))

	//Preamble
	val preamble0 = "b10101010".U
	val preamble1 = "b01010101".U

	//Handshake parameters
	val in_ready = RegInit(false.B)
	val out_valid = RegInit(false.B)

	//data registers
	val data_w = Wire(UInt(8.W))
	val data_r = RegInit(0.U(8.W))

	//CRC
	val crc_reset = Wire(Bool())
	val crc_data = Wire(UInt(1.W))
	val crc_valid = Wire(Bool())
	val crc_result = Wire(UInt(24.W))
	val crc_seed = Wire(UInt(24.W))

	//whitening
	val white_reset = Wire(Bool())
	val white_data = Wire(UInt(1.W))
	val white_valid = Wire(Bool())	
	val white_result = Wire(UInt(1.W))
	val white_seed = Wire(UInt(7.W))			

	//decouple assignments
	io.in.data.ready := in_ready
	io.out.valid := out_valid


	when(state === idle){
		io.out.bits := 0.U
	}.otherwise{
		when(state === pdu_header || state === pdu_payload || state === crc){
			io.out.bits := white_result
		}.otherwise{//PREAMBLE, aa
			io.out.bits := data_r(counter_byte_r)
		}
	}

	when(state === crc && counter_r === 2.U && counter_byte_r === 7.U && io.out.fire() === true.B){
		io.in.done := true.B	
	}.otherwise{
		io.in.done := false.B
	}

	//default
	counter_w 		:= counter_r
	counter_byte_w	:= counter_byte_r
	data_w			:= data_r

	//StateTransition with counter updates
	when(state === idle){
		when(io.in.trigger === true.B){
			state := preamble
			counter_w := 0.U
			counter_byte_w := 0.U
		}.otherwise{
			state := idle
		}
	}.elsewhen(state === preamble){
		when(counter_r === 0.U && counter_byte_r === 7.U && io.out.fire() === true.B){//note
			state := aa
			counter_w := 0.U
			counter_byte_w := 0.U
		}.otherwise{
			state := preamble
			when(io.out.fire() === true.B){
				when(counter_byte_r === 7.U){
					counter_w := counter_r + 1.U
					counter_byte_w := 0.U
				}.otherwise{
					counter_byte_w := counter_byte_r + 1.U					
				}
			}
		}		
	}.elsewhen(state === aa){
		when(counter_r === 3.U && counter_byte_r === 7.U && io.out.fire() === true.B){//note
			state := pdu_header
			counter_w := 0.U
			counter_byte_w := 0.U
		}.otherwise{
			state := aa
			when(io.out.fire() === true.B){
				when(counter_byte_r === 7.U){
					counter_w := counter_r + 1.U
					counter_byte_w := 0.U
				}.otherwise{
					counter_byte_w := counter_byte_r + 1.U					
				}
			}				
		}			
	}.elsewhen(state === pdu_header){
		when(counter_r === 1.U && counter_byte_r === 7.U && io.out.fire() === true.B){//note
			state := pdu_payload
			counter_w := 0.U
			counter_byte_w := 0.U
		}.otherwise{
			state := pdu_header
			when(io.out.fire() === true.B){
				when(counter_byte_r === 7.U){
					counter_w := counter_r + 1.U
					counter_byte_w := 0.U
				}.otherwise{
					counter_byte_w := counter_byte_r + 1.U					
				}
			}
		}			
	}.elsewhen(state === pdu_payload){
		when(counter_r === pdu_length - 1.U && counter_byte_r === 7.U && io.out.fire() === true.B){//note
			state := crc
			counter_w := 0.U
			counter_byte_w := 0.U
		}.otherwise{
			state := pdu_payload
			when(io.out.fire() === true.B){
				when(counter_byte_r === 7.U){
					counter_w := counter_r + 1.U
					counter_byte_w := 0.U
				}.otherwise{
					counter_byte_w := counter_byte_r + 1.U					
				}
			}
		}			
	}.elsewhen(state === crc){
		when(counter_r === 2.U && counter_byte_r === 7.U && io.out.fire() === true.B){//note
			state := idle
			counter_w := 0.U
			counter_byte_w := 0.U
		}.otherwise{
			state := crc
			when(io.out.fire() === true.B){
				when(counter_byte_r === 7.U){
					counter_w := counter_r + 1.U
					counter_byte_w := 0.U
				}.otherwise{
					counter_byte_w := counter_byte_r + 1.U					
				}
			}
		}		
	}.otherwise{
		state := idle//error
	}


	//PDU_Length
	when(state === pdu_header && counter_r === 1.U){
		pdu_length := data_r
	}.otherwise{
		//do nothing: registers preserve value//note
	}

	//in_ready//note:check corner cases
	when(state === aa || state === pdu_header || state === pdu_payload){
		when(state === pdu_payload && counter_r === pdu_length-1.U && counter_byte_r === 7.U && io.out.fire() === true.B){
			in_ready := false.B//special case at the end of PAYLOAD		
		}.elsewhen(counter_byte_r === 7.U && io.out.fire() === true.B){
			in_ready := true.B
		}.elsewhen(io.in.data.fire() === true.B){
			in_ready := false.B		
		}.otherwise{
			//do nothing
		}		
	}.otherwise{//IDLE, PREAMBLE, CRC
		when(state === PREAMBLE && counter_r === 0.U && counter_byte_r === 7.U && io.out.fire() === true.B){
			in_ready := true.B//special case at the end of PREAMBLE: aa starts with ready
		}.otherwise{
			in_ready := false.B
		}
	}

	//output
	when(state === idle){
		out_valid := false.B
	}.elsewhen(state === preamble){
		when(counter_r === 0.U && counter_byte_r === 7.U && io.out.fire() === true.B){
			out_valid := false.B//special case at the end of PREAMBLE: aa starts with invalid
		}.otherwise{
			out_valid := true.B
		}
	}.elsewhen(state === crc){
		when(counter_r === 2.U && counter_byte_r === 7.U && io.out.fire() === true.B){
			out_valid := false.B//special case at the end of CRC		
		}.otherwise{
			out_valid := true.B			
		}
	}.otherwise{//aa, pdu_header, pdu_payload
		when(counter_byte_r === 7.U && io.out.fire() === true.B){
			out_valid := false.B			
		}.elsewhen(io.in.data.fire() === true.B){
			out_valid := true.B				
		}
	}

	//data
	when(state === aa || state === pdu_header || state === pdu_payload){
		when(io.in.data.fire()){
			data_w := io.in.data.bits			
		}.otherwise{
			data_w := data_r
		}
	}.elsewhen(state === preamble){
		when(io.in.data.bits(0) === 0.U){//note: problems when not firing
			data_w := preamble0
		}.otherwise{
			data_w := preamble1
		}
	}.elsewhen(state === crc){
		when(counter_w === 0.U){
			data_w := crc_result(7,0)
		}.elsewhen(counter_w === 1.U){
			data_w := crc_result(15,8)				
		}.elsewhen(counter_w === 2.U){
			data_w := crc_result(23,16)							
		}.otherwise{
			data_w := crc_result(7,0)//error
		}
	}.otherwise{//IDLE
		data_w := 0.U//or preserve
	}

	//CRC
	crc_reset := io.in.trigger
	when(state === pdu_header || state === pdu_payload){
		crc_data := data_r(counter_byte_r)
		crc_valid := io.out.fire()
	}.otherwise{
		crc_data := 0.U
		crc_valid := false.B
	}
	//crc_result wires to CRC module
	crc_seed := io.in.bits.crc_seed

	//whitening
	white_reset := io.in.trigger
	when(state === pdu_header || state === pdu_payload || state === crc){	
		white_data  := data_r(counter_byte_r)//note
		white_valid := io.out.fire()
	}.otherwise{
		white_data  := 0.U
		white_valid := false.B
	}
	//white_result wires to WHITE module
	white_seed := io.in.bits.white_seed	


//sequential logic
	counter_r 		:= counter_w
	counter_byte_r	:= counter_byte_w
	data_r			:= data_w

	//CRC instantiate
	val crc = Module(new Serial_CRC)

	crc.io.init := crc_reset
	crc.io.operand.bits := crc_data
	crc.io.operand.valid := crc_valid
	crc_result := crc.io.result.bits
	crc.io.result.ready := true.B
	crc.io.seed := crc_seed

	//whitening instantiate
	val white = Module(new Whitening)

	white.io.init := white_reset
	white.io.operand.bits := white_data
	white.io.operand.valid := white_valid
	white_result := white.io.result.bits
	white.io.result.ready := true.B
	white.io.seed := white_seed
}
