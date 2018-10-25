package PacketAssembler//note

import chisel3._
import chisel3.util._
import CRC._
import Whitening._


class PacketAssembler extends Module {
  val io = IO(new Bundle {
	//DMA, REG
    val DMA_Trigger_i = Input(Bool())
    val DMA_Data_i = Flipped(DecoupledIO(UInt(8.W)))//decouple(sink): data, pop, empty
    //val DMA_Data_i = DecoupledIO(UInt(8.W)).flip()
    val REG_CRC_Seed_i = Input(UInt(24.W))
    val REG_White_Seed_i = Input(UInt(7.W))

    val DMA_Done_o = Output(Bool())	
	
    //AFIFO
    val AFIFO_Data_o = Decoupled(UInt(1.W))//decouple(source): data, puch, full

  })

//scala declaration
	//state parameter
	//val IDLE :: PREAMBLE :: AA :: PDU_HEADER :: PDU_PAYLOAD :: CRC :: Nil = Enum(6)
	val IDLE = Wire(UInt(3.W))
	val PREAMBLE = Wire(UInt(3.W))
	val AA = Wire(UInt(3.W))
	val PDU_HEADER = Wire(UInt(3.W))
	val PDU_PAYLOAD = Wire(UInt(3.W))
	val CRC = Wire(UInt(3.W))
	IDLE := 0.U
	PREAMBLE := 1.U
	AA := 2.U
	PDU_HEADER := 3.U
	PDU_PAYLOAD := 4.U
	CRC := 5.U

	val initial_state = IDLE
	val state_list = List(IDLE, PREAMBLE, AA, PDU_HEADER, PDU_PAYLOAD, CRC)

	//reg, wire
	//FSM
	val state_w = Wire(UInt(3.W))
	val state_r = RegInit(UInt(3.W), initial_state)

	val counter_w = Wire(UInt(8.W))//at most 255 for PDU
	val counter_r = RegInit(UInt(8.W), 0.U)

	val counter_byte_w = Wire(UInt(3.W))//byte in bit out
	val counter_byte_r = RegInit(UInt(3.W), 0.U)	

	val PDU_Length_r = RegInit(UInt(8.W), 0.U)

	//Preamble
	val Preamble0 = Wire(UInt(8.W))
	val Preamble1 = Wire(UInt(8.W))

	//DMA_Data
	val DMA_Data_Ready_r = RegInit(Bool(), false.B)
	val DMA_Data_Fire_w = Wire(Bool())

	//AFIFO
	val AFIFO_Valid_r = RegInit(Bool(), false.B)
	val AFIFO_Fire_w = Wire(Bool())

	//data registers
	val data_w = Wire(UInt(8.W))
	val data_r = RegInit(UInt(8.W), 0.U)

	//CRC
	val CRC_Reset_w = Wire(Bool())
	val CRC_Data_w = Wire(UInt(1.W))
	val CRC_Valid_w = Wire(Bool())
	val CRC_Result_w = Wire(UInt(24.W))
	val CRC_Seed_w = Wire(UInt(24.W))

	//whitening
	val WHITE_Reset_w = Wire(Bool())
	val WHITE_Data_w = Wire(UInt(1.W))
	val WHITE_Valid_w = Wire(Bool())	
	val WHITE_Result_w = Wire(UInt(1.W))
	val WHITE_Seed_w = Wire(UInt(7.W))			

	//decouple assignments
	io.DMA_Data_i.ready := DMA_Data_Ready_r
	DMA_Data_Fire_w := io.DMA_Data_i.ready & io.DMA_Data_i.valid	
	io.AFIFO_Data_o.valid := AFIFO_Valid_r
	AFIFO_Fire_w := io.AFIFO_Data_o.ready & io.AFIFO_Data_o.valid

	//Preamble assignments
	Preamble0 := "b10101010".U
	Preamble1 := "b01010101".U


//combinational logic
	/*for (states_num <- state_list){
		when (state_r === state_num.U){
			//state_w := StateTransition(input_bundle, val_bundle).U
			output_bundle := OutputFunction(input_bundle, val_bundle)
			val_bundle := CombLogic(input_bundle, val_bundle)
		}
	}
	output_bundle <> OutputFunction(input_bundle, val_bundle)
	val_bundle <> CombLogic(input_bundle, val_bundle)*/

	//output function
	when(state_r === IDLE){
		io.AFIFO_Data_o.bits := 0.U
	}.otherwise{
		when(state_r === PDU_HEADER || state_r === PDU_PAYLOAD || state_r === CRC){
			io.AFIFO_Data_o.bits := WHITE_Result_w
		}.otherwise{//PREAMBLE, AA
			io.AFIFO_Data_o.bits := data_r(counter_byte_r)
		}
	}

	when(state_r === CRC && counter_r === 2.U && counter_byte_r === 7.U && AFIFO_Fire_w === true.B){
		io.DMA_Done_o := true.B	
	}.otherwise{
		io.DMA_Done_o := false.B
	}

	//default
	state_w 		:= state_r
	counter_w 		:= counter_r
	counter_byte_w	:= counter_byte_r
	data_w			:= data_r
	PDU_Length_r	:= PDU_Length_r//note: preserve value
	DMA_Data_Ready_r:= DMA_Data_Ready_r
	AFIFO_Valid_r	:= AFIFO_Valid_r

	//StateTransition with counter updates
	when(state_r === IDLE){
		when(io.DMA_Trigger_i === true.B){
			state_w := PREAMBLE
			counter_w := 0.U
			counter_byte_w := 0.U
		}.otherwise{
			state_w := IDLE
		}
	}.elsewhen(state_r === PREAMBLE){
		when(counter_r === 0.U && counter_byte_r === 7.U && AFIFO_Fire_w === true.B){//note
			state_w := AA
			counter_w := 0.U
			counter_byte_w := 0.U
		}.otherwise{
			state_w := PREAMBLE
			when(AFIFO_Fire_w === true.B){
				when(counter_byte_r === 7.U){
					counter_w := counter_r+1.U
					counter_byte_w := 0.U
				}.otherwise{
					counter_byte_w := counter_byte_r+1.U					
				}
			}
		}		
	}.elsewhen(state_r === AA){
		when(counter_r === 3.U && counter_byte_r === 7.U && AFIFO_Fire_w === true.B){//note
			state_w := PDU_HEADER
			counter_w := 0.U
			counter_byte_w := 0.U
		}.otherwise{
			state_w := AA
			when(AFIFO_Fire_w === true.B){
				when(counter_byte_r === 7.U){
					counter_w := counter_r+1.U
					counter_byte_w := 0.U
				}.otherwise{
					counter_byte_w := counter_byte_r+1.U					
				}
			}				
		}			
	}.elsewhen(state_r === PDU_HEADER){
		when(counter_r === 1.U && counter_byte_r === 7.U && AFIFO_Fire_w === true.B){//note
			state_w := PDU_PAYLOAD
			counter_w := 0.U
			counter_byte_w := 0.U
		}.otherwise{
			state_w := PDU_HEADER
			when(AFIFO_Fire_w === true.B){
				when(counter_byte_r === 7.U){
					counter_w := counter_r+1.U
					counter_byte_w := 0.U
				}.otherwise{
					counter_byte_w := counter_byte_r+1.U					
				}
			}
		}			
	}.elsewhen(state_r === PDU_PAYLOAD){
		when(counter_r === PDU_Length_r-1.U && counter_byte_r === 7.U && AFIFO_Fire_w === true.B){//note
			state_w := CRC
			counter_w := 0.U
			counter_byte_w := 0.U
		}.otherwise{
			state_w := PDU_PAYLOAD
			when(AFIFO_Fire_w === true.B){
				when(counter_byte_r === 7.U){
					counter_w := counter_r+1.U
					counter_byte_w := 0.U
				}.otherwise{
					counter_byte_w := counter_byte_r+1.U					
				}
			}
		}			
	}.elsewhen(state_r === CRC){
		when(counter_r === 2.U && counter_byte_r === 7.U && AFIFO_Fire_w === true.B){//note
			state_w := IDLE
			counter_w := 0.U
			counter_byte_w := 0.U
		}.otherwise{
			state_w := CRC
			when(AFIFO_Fire_w === true.B){
				when(counter_byte_r === 7.U){
					counter_w := counter_r+1.U
					counter_byte_w := 0.U
				}.otherwise{
					counter_byte_w := counter_byte_r+1.U					
				}
			}
		}		
	}.otherwise{
		state_w := IDLE//error
	}


	//PDU_Length
	when(state_r === PDU_HEADER && counter_r === 1.U){
		PDU_Length_r := data_r
	}.otherwise{
		//do nothing: registers preserve value//note
	}

	//DMA_Data_Ready_r//note:check corner cases
	when(state_r === AA || state_r === PDU_HEADER || state_r === PDU_PAYLOAD){
		when(state_r === PDU_PAYLOAD && counter_r === PDU_Length_r-1.U && counter_byte_r === 7.U && AFIFO_Fire_w === true.B){
			DMA_Data_Ready_r := false.B//special case at the end of PAYLOAD		
		}.elsewhen(counter_byte_r === 7.U && AFIFO_Fire_w === true.B){
			DMA_Data_Ready_r := true.B
		}.elsewhen(DMA_Data_Fire_w === true.B){
			DMA_Data_Ready_r := false.B		
		}.otherwise{
			//do nothing
		}		
	}.otherwise{//IDLE, PREAMBLE, CRC
		when(state_r === PREAMBLE && counter_r === 0.U && counter_byte_r === 7.U && AFIFO_Fire_w === true.B){
			DMA_Data_Ready_r := true.B//special case at the end of PREAMBLE: AA starts with ready
		}.otherwise{
			DMA_Data_Ready_r := false.B
		}
	}

	//AFIFO_Valid_w//note:check corner cases
		//AFIFO_Valid_w := ~DMA_Data_Ready_r
	when(state_r === IDLE){
		AFIFO_Valid_r := false.B
	}.elsewhen(state_r === PREAMBLE){
		when(counter_r === 0.U && counter_byte_r === 7.U && AFIFO_Fire_w === true.B){
			AFIFO_Valid_r := false.B//special case at the end of PREAMBLE: AA starts with invalid
		}.otherwise{
			AFIFO_Valid_r := true.B
		}
	}.elsewhen(state_r === CRC){
		when(counter_r === 2.U && counter_byte_r === 7.U && AFIFO_Fire_w === true.B){
			AFIFO_Valid_r := false.B//special case at the end of CRC		
		}.otherwise{
			AFIFO_Valid_r := true.B			
		}
	}.otherwise{//AA, PDU_HEADER, PDU_PAYLOAD
		when(counter_byte_r === 7.U && AFIFO_Fire_w === true.B){
			AFIFO_Valid_r := false.B			
		}.elsewhen(DMA_Data_Fire_w === true.B){
			AFIFO_Valid_r := true.B				
		}
	}

	//data
	when(state_r === AA || state_r === PDU_HEADER || state_r === PDU_PAYLOAD){
		when(DMA_Data_Fire_w === true.B){
			data_w := io.DMA_Data_i.bits			
		}.otherwise{
			data_w := data_r
		}
	}.elsewhen(state_r === PREAMBLE){
		when(io.DMA_Data_i.bits(0) === 0.U){//note: problems when not firing
			data_w := Preamble0
		}.otherwise{
			data_w := Preamble1
		}
	}.elsewhen(state_r === CRC){
		//data_w := CRC_Result_w((counter_w+1)*8-1,counter_w*8)//note
		when(counter_w === 0.U){
			data_w := CRC_Result_w(7,0)
		}.elsewhen(counter_w === 1.U){
			data_w := CRC_Result_w(15,8)				
		}.elsewhen(counter_w === 2.U){
			data_w := CRC_Result_w(23,16)							
		}.otherwise{
			data_w := CRC_Result_w(7,0)//error
		}
	}.otherwise{//IDLE
		data_w := 0.U//or preserve
	}

	//CRC
	CRC_Reset_w := io.DMA_Trigger_i
	when(state_r === PDU_HEADER || state_r === PDU_PAYLOAD){
		CRC_Data_w := data_r(counter_byte_r)
		CRC_Valid_w := AFIFO_Fire_w
	}.otherwise{
		CRC_Data_w := 0.U
		CRC_Valid_w := false.B
	}
	//CRC_Result_w wires to CRC module
	CRC_Seed_w := io.REG_CRC_Seed_i

	//whitening
	WHITE_Reset_w := io.DMA_Trigger_i
	when(state_r === PDU_HEADER || state_r === PDU_PAYLOAD || state_r === CRC){	
		WHITE_Data_w  := data_r(counter_byte_r)//note
		WHITE_Valid_w := AFIFO_Fire_w
	}.otherwise{
		WHITE_Data_w  := 0.U
		WHITE_Valid_w := false.B
	}
	//WHITE_Result_w wires to WHITE module
	WHITE_Seed_w := io.REG_White_Seed_i		


//sequential logic
	state_r 		:= state_w
	counter_r 		:= counter_w
	counter_byte_r	:= counter_byte_w
	data_r			:= data_w



	/*def OutputFunction(input_bundle: Class_input_bundle, val_bundle: Class_val_bundle): Class_output_bundle ={//note
		//OutputFunction.AFIFO_Data.valid := AFIFO_Valid_w
	}

	def CombLogic(input_bundle: Class_input_bundle, val_bundle: Class_val_bundle): Class_val_bundle ={	

	}*/

	//CRC instantiate
	val CRC_inst = Module(new Serial_CRC)

	CRC_inst.io.init := CRC_Reset_w
	CRC_inst.io.operand.bits := CRC_Data_w
	CRC_inst.io.operand.valid := CRC_Valid_w
	CRC_Result_w := CRC_inst.io.result.bits
	CRC_inst.io.result.ready := true.B
	CRC_inst.io.seed := CRC_Seed_w

	//whitening instantiate
	val WHITE_inst = Module(new Whitening)

	WHITE_inst.io.init := WHITE_Reset_w
	WHITE_inst.io.operand.bits := WHITE_Data_w
	WHITE_inst.io.operand.valid := WHITE_Valid_w
	WHITE_Result_w := WHITE_inst.io.result.bits
	WHITE_inst.io.result.ready := true.B
	WHITE_inst.io.seed := WHITE_Seed_w

/*
//for testing
	//CRC instantiate
	val CRC_inst = Module(new CRC_TestModule)

	CRC_inst.io.init := CRC_Reset_w
	CRC_inst.io.operand.bits := CRC_Data_w
	CRC_inst.io.operand.valid := CRC_Valid_w
	CRC_Result_w := CRC_inst.io.result
	CRC_inst.io.seed := CRC_Seed_w

	//whitening instantiate
	val WHITE_inst = Module(new Whitening_TestModule)

	WHITE_inst.io.init := WHITE_Reset_w
	WHITE_inst.io.operand.bits := WHITE_Data_w
	WHITE_inst.io.operand.valid := WHITE_Valid_w
	WHITE_Result_w := WHITE_inst.io.result
	WHITE_inst.io.seed := WHITE_Seed_w
*/
}

/*
//CRC, Whitening modules for testing
class CRC_TestModule extends Module {
    val io      = IO(new Bundle {
    val operand  = new DecoupledIO(UInt(1.W)).flip()
    val result   = Output(UInt(24.W))

   	val seed       = Input(UInt(24.W))
    val init       = Input(Bool())        // to init the seed

  // val crc_out = Output(UInt(24.W))
  })

	io.operand.ready := true.B
	io.result := "h101001".U
}

class Whitening_TestModule extends Module {
    val io      = IO(new Bundle {
    val operand  = new DecoupledIO(UInt(1.W)).flip()
    val result   = Output(UInt(1.W))

    val seed       = Input(UInt(7.W))
    val init       = Input(Bool())        // to init the seed
  })

    io.operand.ready := true.B
    io.result := ~io.operand.bits
}
*/
/*
//old bundle declarations
class valdecouple_class extends Bundle{
	val bits = Wire(UInt(8.W))
	val ready = Wire(Bool())
	val valid = Wire(Bool())		
}


class input_bundle_class extends Bundle{
    val DMA_Trigger = Wire(Bool())
    //val DMA_Data = Flipped(Decoupled(UInt(8.W)))//decouple(sink): data, pop, empty//note
    val DMA_Data = new valdecouple_class
    //val DMA_Data = DecoupledIO(UInt(8.W)).flip()//decouple(sink): data, pop, empty//note
    val REG_CRC_Seed = Wire(UInt(24.W))
    val REG_White_Seed = Wire(UInt(7.W))	
}


class output_bundle_class extends Bundle{
    val DMA_Done = Wire(Bool())	
    //val AFIFO_Data = Decoupled(UInt(1.W))//decouple(source): data, puch, full
    val AFIFO_Data = new valdecouple_class
}

class val_bundle_class extends Bundle{
//reg, wire
	//FSM
	val state_w = Wire(UInt(3.W))
	//val state_r = RegInit(UInt(3.W), initial_state)
	val state_r = RegInit(UInt(3.W), 0.U)

	val counter_w = Wire(UInt(8.W))//at most 255 for PDU
	val counter_r = RegInit(UInt(8.W), 0.U)

	val counter_byte_w = Wire(UInt(3.W))//byte in bit out
	val counter_byte_r = RegInit(UInt(3.W), 0.U)	

	val PDU_Length_r = RegInit(UInt(8.W), 0.U)

	//Preamble
	val Preamble0 = Wire(UInt(8.W))
	val Preamble1 = Wire(UInt(8.W))

	//DMA_Data
	val DMA_Data_Ready_r = RegInit(Bool(), false.B)
	val DMA_Data_Fire_w = Wire(Bool())

	//AFIFO
	val AFIFO_Valid_r = RegInit(Bool(), false.B)
	val AFIFO_Fire_w = Wire(Bool())

	//data registers
	val data_w = Wire(UInt(8.W))
	val data_r = RegInit(UInt(8.W), 0.U)

	//CRC
	val CRC_Reset_w = Wire(Bool())
	val CRC_Data_w = Wire(UInt(1.W))
	val CRC_Valid_w = Wire(Bool())
	val CRC_Result_w = Wire(UInt(24.W))
	val CRC_Seed_w = Wire(UInt(24.W))

	//whitening
	val WHITE_Reset_w = Wire(Bool())
	val WHITE_Data_w = Wire(UInt(1.W))
	val WHITE_Valid_w = Wire(Bool())	
	val WHITE_Result_w = Wire(UInt(1.W))
	val WHITE_Seed_w = Wire(UInt(7.W))			
}
*/
