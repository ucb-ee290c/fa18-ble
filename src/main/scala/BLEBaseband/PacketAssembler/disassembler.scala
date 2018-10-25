package PacketAssembler//note

import chisel3._
import chisel3.util._
import CRC._
import Whitening._


class PacketDisAssembler extends Module {
  val io = IO(new Bundle {
    //DMA, REG
    val DMA_Switch_i = Input(Bool())
    val REG_AA_i = Input(UInt(32.W))   
    val REG_CRC_Seed_i = Input(UInt(24.W))
    val REG_DeWhite_Seed_i = Input(UInt(7.W))

    val DMA_Data_o = DecoupledIO(UInt(8.W))//decouple(sink): data, puch, full
    val DMA_Length_o = Decoupled(UInt(8.W))
    val DMA_Flag_AA_o = Decoupled(Bool())
    val DMA_Flag_CRC_o = Decoupled(Bool())     

    //AFIFO
    val AFIFO_Data_i = Flipped(DecoupledIO(UInt(1.W)))//decouple(source): data, pop, empty
})

/*
//testing assignments
io.DMA_Data_o.bits := 0.U
io.DMA_Data_o.valid := true.B
io.DMA_Length_o.bits := 0.U
io.DMA_Length_o.valid := true.B
io.DMA_Flag_AA_o.bits := true.B
io.DMA_Flag_AA_o.valid := true.B
io.DMA_Flag_CRC_o.bits := true.B
io.DMA_Flag_CRC_o.valid := true.B
io.AFIFO_Data_i.ready := true.B
*/

//scala declaration(note: can be a class)
  //state parameter
  //val IDLE :: PREAMBLE :: AA :: PDU_HEADER :: PDU_PAYLOAD :: CRC :: Nil = Enum(6)
  val IDLE = Wire(UInt(3.W))
  val PREAMBLE = Wire(UInt(3.W))
  val AA = Wire(UInt(3.W))
  val PDU_HEADER = Wire(UInt(3.W))
  val PDU_PAYLOAD = Wire(UInt(3.W))
  val CRC = Wire(UInt(3.W))
  val WAIT_DMA = Wire(UInt(3.W))
  IDLE := 0.U
  PREAMBLE := 1.U
  AA := 2.U
  PDU_HEADER := 3.U
  PDU_PAYLOAD := 4.U
  CRC := 5.U
  WAIT_DMA := 6.U

  val initial_state = IDLE
  val state_list = List(IDLE, PREAMBLE, AA, PDU_HEADER, PDU_PAYLOAD, CRC, WAIT_DMA)

  //reg, wire
  //FSM
  val state_w = Wire(UInt(3.W))
  val state_r = RegInit(UInt(3.W), initial_state)

  val counter_w = Wire(UInt(8.W))//at most 255 for PDU
  val counter_r = RegInit(UInt(8.W), 0.U)

  val counter_byte_w = Wire(UInt(3.W))//bit in byte out
  val counter_byte_r = RegInit(UInt(3.W), 0.U)  

  //packet status
  val PDU_Length_r = RegInit(UInt(8.W), 0.U)
  val PDU_Length_Valid_r = RegInit(Bool(), false.B)
  val Flag_AA_r = RegInit(Bool(), false.B)
  val Flag_AA_Valid_r = RegInit(Bool(), false.B)
  val Flag_CRC_r = RegInit(Bool(), false.B)
  val Flag_CRC_Valid_r = RegInit(Bool(), false.B)

  //Preamble
  val Preamble0 = Wire(UInt(8.W))
  val Preamble1 = Wire(UInt(8.W))
  val Preamble01 = Wire(UInt(8.W))

  //DMA_Data
  val DMA_Data_Valid_r = RegInit(Bool(), false.B)
  val DMA_Data_Fire_w = Wire(Bool())

  //AFIFO
  val AFIFO_Ready_r = RegInit(Bool(), false.B)
  val AFIFO_Fire_w = Wire(Bool())

  //data registers
  //val data_w = Wire(UInt(8.W))
  val data_w = Wire(Vec(8, Bool()))
  val data_r = RegInit(UInt(8.W), 0.U)

  //CRC
  val CRC_Reset_w = Wire(Bool())
  val CRC_Data_w = Wire(UInt(1.W))
  val CRC_Valid_w = Wire(Bool())
  val CRC_Result_w = Wire(UInt(24.W))
  val CRC_Seed_w = Wire(UInt(24.W))

  //whitening
  val DEWHITE_Reset_w = Wire(Bool())
  val DEWHITE_Data_w = Wire(UInt(1.W))
  val DEWHITE_Valid_w = Wire(Bool())  
  val DEWHITE_Result_w = Wire(UInt(1.W))
  val DEWHITE_Seed_w = Wire(UInt(7.W))      

  //assignments
    //Decouple firing
  DMA_Data_Fire_w := io.DMA_Data_o.ready & io.DMA_Data_o.valid 
  AFIFO_Fire_w := io.AFIFO_Data_i.ready & io.AFIFO_Data_i.valid
  //PDU_Length_Fire_w := io.DMA_Length_o.ready & io.DMA_Length_o.valid
  //Flag_AA_Fire_w := io.DMA_Flag_AA_o.ready & io.DMA_Flag_AA_o.valid
  //Flag_CRC_Fire_w := io.DMA_Flag_CRC_o.ready & io.DMA_Flag_CRC_o.valid

    //preamble hard code
  Preamble0 := "b10101010".U
  Preamble1 := "b01010101".U
  when(io.REG_AA_i(0) === 0.U){
    Preamble01 := Preamble0    
  }.otherwise{
    Preamble01 := Preamble1     
  }


  //output function
  when(state_r === IDLE || state_r === PREAMBLE){
    io.DMA_Data_o.bits := 0.U
  }.otherwise{//AA, PDU_HEADER, PDU_PAYLOAD, CRC
    io.DMA_Data_o.bits := data_r 
  }

  io.DMA_Length_o.bits := PDU_Length_r
  io.DMA_Length_o.valid := PDU_Length_Valid_r
  io.DMA_Flag_AA_o.bits := Flag_AA_r
  io.DMA_Flag_AA_o.valid := Flag_AA_Valid_r
  io.DMA_Flag_CRC_o.bits := Flag_CRC_r
  io.DMA_Flag_CRC_o.valid := Flag_CRC_Valid_r

  io.DMA_Data_o.valid := DMA_Data_Valid_r
  io.AFIFO_Data_i.ready := AFIFO_Ready_r

  //default
  state_w      := state_r
  counter_w    := counter_r
  counter_byte_w := counter_byte_r
  data_w     := data_r.toBools

  PDU_Length_r   := PDU_Length_r//note: preserve value
  PDU_Length_Valid_r := PDU_Length_Valid_r 
  Flag_AA_r := Flag_AA_r
  Flag_AA_Valid_r := Flag_AA_Valid_r
  Flag_CRC_r := Flag_CRC_r
  Flag_CRC_Valid_r := Flag_CRC_Valid_r 

  DMA_Data_Valid_r := DMA_Data_Valid_r
  AFIFO_Ready_r  := AFIFO_Ready_r

  //StateTransition with counter updates
    //Note: counter_r updates when DMA_Data_Fire_w
    //Note: counter_byte_r updates when AFIFO_Fire_w
  when(state_r === IDLE){
    PDU_Length_r := 0.U
    PDU_Length_Valid_r := false.B
    Flag_AA_r := false.B
    Flag_AA_Valid_r := false.B
    Flag_CRC_r := false.B
    Flag_CRC_Valid_r := false.B
    when(io.DMA_Switch_i === true.B){//note: DMA_Switch_i usage
      state_w := PREAMBLE
    }.otherwise{
      state_w := IDLE
    }
  }.elsewhen(state_r === PREAMBLE){
    when(data_w.asUInt === Preamble01){//note: data_w or data_r
      state_w := AA
      counter_w := 0.U
      counter_byte_w := 0.U
    }.otherwise{
      state_w := PREAMBLE
    }   
  }.elsewhen(state_r === AA){
    when(counter_r === 3.U && DMA_Data_Fire_w === true.B){//note
      state_w := PDU_HEADER
      counter_w := 0.U
      counter_byte_w := 0.U
    }.otherwise{
      state_w := AA
      when(DMA_Data_Fire_w === true.B){
        counter_w := counter_r+1.U     
      }
      when(AFIFO_Fire_w === true.B){
        when(counter_byte_r === 7.U){
          counter_byte_w := 0.U
        }.otherwise{
          counter_byte_w := counter_byte_r+1.U
        }
      }       
    }     
  }.elsewhen(state_r === PDU_HEADER){
    when(counter_r === 1.U && DMA_Data_Fire_w === true.B){//note
      state_w := PDU_PAYLOAD
      counter_w := 0.U
      counter_byte_w := 0.U
    }.otherwise{
      state_w := PDU_HEADER
      when(DMA_Data_Fire_w === true.B){
        counter_w := counter_r+1.U     
      }
      when(AFIFO_Fire_w === true.B){
        when(counter_byte_r === 7.U){
          counter_byte_w := 0.U
        }.otherwise{
          counter_byte_w := counter_byte_r+1.U
        }
      }       
    }     
  }.elsewhen(state_r === PDU_PAYLOAD){
    when(counter_r === PDU_Length_r-1.U && DMA_Data_Fire_w === true.B){//note
      state_w := CRC
      counter_w := 0.U
      counter_byte_w := 0.U
    }.otherwise{
      state_w := PDU_PAYLOAD
      when(DMA_Data_Fire_w === true.B){
        counter_w := counter_r+1.U     
      }
      when(AFIFO_Fire_w === true.B){
        when(counter_byte_r === 7.U){
          counter_byte_w := 0.U
        }.otherwise{
          counter_byte_w := counter_byte_r+1.U
        }
      }         
    }     
  }.elsewhen(state_r === CRC){
    when(counter_r === 2.U && DMA_Data_Fire_w === true.B){//note
      state_w := WAIT_DMA
      counter_w := 0.U
      counter_byte_w := 0.U
    }.otherwise{
      state_w := CRC
      when(DMA_Data_Fire_w === true.B){
        counter_w := counter_r+1.U     
      }
      when(AFIFO_Fire_w === true.B){
        when(counter_byte_r === 7.U){
          counter_byte_w := 0.U
        }.otherwise{
          counter_byte_w := counter_byte_r+1.U
        }
      }       
    }   
  }.elsewhen(state_r === WAIT_DMA) {
    when (io.DMA_Length_o.ready === true.B && io.DMA_Flag_AA_o.ready === true.B && io.DMA_Flag_CRC_o.ready === true.B) {
      state_w := IDLE
    }.otherwise {
      state_w := WAIT_DMA
    }
  }.otherwise{
    state_w := IDLE//error
  }

    //PDU_Length
  //when(state_r === PDU_PAYLOAD && counter_r === 0.U && counter_byte_r === 0.U){//note: can change to intuitive statement(add fire_w) with data_w
  when(state_r === PDU_HEADER && counter_r === 1.U && DMA_Data_Fire_w === true.B){//note: can change to intuitive statement(add fire_w) with data_w
    PDU_Length_r := data_r
    PDU_Length_Valid_r := true.B
  }.elsewhen(state_w === IDLE) {
    PDU_Length_Valid_r := false.B
  }.otherwise{
    //do nothing: registers preserve value//note
  }

    //Flag_AA
  when(state_r === AA && counter_r === 0.U && DMA_Data_Fire_w === true.B){//note: same as above
    when(data_r =/= io.REG_AA_i(7,0)){
      Flag_AA_r := true.B
      Flag_AA_Valid_r := true.B      
    }
  }.elsewhen(state_r === AA && counter_r === 1.U && DMA_Data_Fire_w === true.B){
    when(data_r =/= io.REG_AA_i(15,8)){
      Flag_AA_r := true.B
      Flag_AA_Valid_r := true.B      
    }    
  }.elsewhen(state_r === AA && counter_r === 2.U && DMA_Data_Fire_w === true.B){
    when(data_r =/= io.REG_AA_i(23,16)){
      Flag_AA_r := true.B
      Flag_AA_Valid_r := true.B      
    }
  }.elsewhen(state_r === AA && counter_r === 3.U && DMA_Data_Fire_w === true.B){
    when(data_r =/= io.REG_AA_i(31,24)){
      Flag_AA_r := true.B
      Flag_AA_Valid_r := true.B      
    }.otherwise{
      Flag_AA_Valid_r := true.B        
    }
  }.otherwise{
    //do nothing: registers preserve value//note
  }

    //Flag_CRC
  when(state_r === CRC && counter_r === 0.U && DMA_Data_Fire_w === true.B){//note: same as above
    when(data_r =/= CRC_Result_w(7,0)){
      Flag_CRC_r := true.B
      Flag_CRC_Valid_r := true.B      
    }
  }.elsewhen(state_r === CRC && counter_r === 1.U && DMA_Data_Fire_w === true.B){
    when(data_r =/= CRC_Result_w(15,8)){
      Flag_CRC_r := true.B
      Flag_CRC_Valid_r := true.B      
    }   
  }.elsewhen(state_r === CRC && counter_r === 2.U && DMA_Data_Fire_w === true.B){
    when(data_r =/= CRC_Result_w(23,16)){
      Flag_CRC_r := true.B
      Flag_CRC_Valid_r := true.B      
    }.otherwise{
      Flag_CRC_Valid_r := true.B        
    }
  }.otherwise{
    //do nothing: registers preserve value//note
  }


  //DMA_Data_Valid_r//note:check corner cases
  when(state_r === IDLE || state_r === PREAMBLE){
    DMA_Data_Valid_r := false.B
  }.otherwise{//AA, PDU_HEADER, PDU_PAYLOAD, CRC
    when(counter_byte_r === 7.U && AFIFO_Fire_w === true.B){
      DMA_Data_Valid_r := true.B
    }.elsewhen(DMA_Data_Fire_w === true.B){
      DMA_Data_Valid_r := false.B
    }
  }

  //AFIFO_Ready_w//note:check corner cases
  when(state_r === IDLE){
    AFIFO_Ready_r := false.B
  }.elsewhen(state_r === PREAMBLE){
    AFIFO_Ready_r := true.B
  }.otherwise{//AA, PDU_HEADER, PDU_PAYLOAD, CRC
    when(counter_byte_r === 7.U && AFIFO_Fire_w === true.B){
      AFIFO_Ready_r := false.B     
    }.elsewhen(DMA_Data_Fire_w === true.B){
      AFIFO_Ready_r := true.B        
    }
  }

  //data
  when(state_r === PDU_HEADER || state_r === PDU_PAYLOAD || state_r === CRC){
    when(AFIFO_Fire_w === true.B){
      //data_w(counter_byte_r) := DEWHITE_Result_w.toBools
      when(DEWHITE_Result_w===0.U){
        data_w(counter_byte_r) := false.B
      }.otherwise{
        data_w(counter_byte_r) := true.B
      }
    }
  }.elsewhen(state_r === PREAMBLE){
    when(AFIFO_Fire_w === true.B){
      //data_w(7) := io.AFIFO_Data_i.bits.toBools //note: subword assignment
      when(io.AFIFO_Data_i.bits===0.U){
        data_w(7) := false.B
      }.otherwise{
        data_w(7) := true.B
      }
      for(i<-0 to 6){//value shifting
        //data_w(i) := data_r(i+1).toBools
        when(data_r(i+1)===0.U){
          data_w(i) := false.B
        }.otherwise{
          data_w(i) := true.B
        }
      }      
    }
  }.elsewhen(state_r === AA){
    when(AFIFO_Fire_w === true.B){
      //data_w(counter_byte_r) := io.AFIFO_Data_i.bits.toBools
      when(io.AFIFO_Data_i.bits===0.U){
        data_w(counter_byte_r) := false.B
      }.otherwise{
        data_w(counter_byte_r) := true.B
      }
    }
  }.otherwise{//IDLE
    //do nothing or := 0.U
  }

  //CRC
  CRC_Reset_w := (state_r === IDLE)
  when(state_r === PDU_HEADER || state_r === PDU_PAYLOAD){//check corner cases
    CRC_Data_w := DEWHITE_Result_w
    CRC_Valid_w := AFIFO_Fire_w
  }.otherwise{
    CRC_Data_w := 0.U
    CRC_Valid_w := false.B
  }
  //CRC_Result_w wires to CRC module
  CRC_Seed_w := io.REG_CRC_Seed_i

  //dewhitening
  DEWHITE_Reset_w := (state_r === IDLE)
  when(state_r === PDU_HEADER || state_r === PDU_PAYLOAD || state_r === CRC){//check corner cases  
    DEWHITE_Data_w  := io.AFIFO_Data_i.bits
    DEWHITE_Valid_w := AFIFO_Fire_w
  }.otherwise{
    DEWHITE_Data_w  := 0.U
    DEWHITE_Valid_w := false.B
  }
  //DEWHITE_Result_w wires to WHITE module
  DEWHITE_Seed_w := io.REG_DeWhite_Seed_i

  //sequential logic
  state_r      := state_w
  counter_r    := counter_w
  counter_byte_r := counter_byte_w
  data_r     := data_w.asUInt

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

  WHITE_inst.io.init := DEWHITE_Reset_w
  WHITE_inst.io.operand.bits := DEWHITE_Data_w
  WHITE_inst.io.operand.valid := DEWHITE_Valid_w
  DEWHITE_Result_w := WHITE_inst.io.result.bits
  WHITE_inst.io.result.ready := true.B
  WHITE_inst.io.seed := DEWHITE_Seed_w

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

  WHITE_inst.io.init := DEWHITE_Reset_w
  WHITE_inst.io.operand.bits := DEWHITE_Data_w
  WHITE_inst.io.operand.valid := DEWHITE_Valid_w
  DEWHITE_Result_w := WHITE_inst.io.result
  WHITE_inst.io.seed := DEWHITE_Seed_w
*/
}
