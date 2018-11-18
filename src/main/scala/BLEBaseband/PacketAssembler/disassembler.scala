package PacketDisAssembler//note

import chisel3._
import chisel3.util._
import CRC._
import Whitening._
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.subsystem.BaseSubsystem

class PDAInputBundle extends Bundle {
	val switch = Output(Bool())
        //val REG_aa_i = Input(UInt(32.W))   
        //val REG_crc_Seed_i = Input(UInt(24.W))
        //val REG_DeWhite_Seed_i = Input(UInt(7.W))
        val data = Output(UInt(1.W))//decouple(source): data, pop, empty
        
	override def cloneType: this.type = PDAInputBundle().asInstanceOf[this.type]
}

object PDAInputBundle {
	def apply(): PDAInputBundle = new PDAInputBundle
}

class PDAOutputBundle extends Bundle {
        val data = Output(UInt(8.W))//decouple(sink): data, puch, full
    	val length = Output(UInt(8.W))
    	val flag_aa = Output(Bool())
    	val flag_crc = Output(Bool()) 

	override def cloneType: this.type = PDAOutputBundle().asInstanceOf[this.type]
}

object PDAOutputBundle {
	def apply(): PDAOutputBundle = new PDAOutputBundle
}

class PacketDisAssemblerIO extends Bundle {
	val in = Flipped(Decoupled(PDAInputBundle()))
	val out = Decoupled(PDAOutputBundle())

	override def cloneType: this.type = PacketDisAssemblerIO().asInstanceOf[this.type]
}

object PacketDisAssemblerIO {
	def apply(): PacketAssemblerIO = new PacketAssemblerIO	
}

trait HasPeripheryPDA extends BaseSubsystem {
  // instantiate cordic chain
  val pdaChain = LazyModule(new PDAThing)
  // connect memory interfaces to pbus
  pbus.toVariableWidthSlave(Some("pdaWrite")) { pdaChain.writeQueue.mem.get }
  pbus.toVariableWidthSlave(Some("pdaRead")) { pdaChain.readQueue.mem.get }
}

class PacketDisAssembler extends Module {
  /*val io = IO(new Bundle {
    //DMA, REG
    val switch = Input(Bool())
    val REG_aa_i = Input(UInt(32.W))   
    val REG_crc_Seed_i = Input(UInt(24.W))
    val REG_DeWhite_Seed_i = Input(UInt(7.W))

    val data = DecoupledIO(UInt(8.W))//decouple(sink): data, puch, full
    val length = Decoupled(UInt(8.W))
    val flag_aa = Decoupled(Bool())
    val flag_crc = Decoupled(Bool())     

    //AFIFO
    val data = Flipped(DecoupledIO(UInt(1.W)))//decouple(source): data, pop, empty
})*/

/*
//testing assignments
io.data.bits := 0.U
io.data.valid := true.B
io.out.bits.length := 0.U
io.out.length.valid := true.B
io.out.bits.flag_aa := true.B
io.out.flag_aa.valid := true.B
io.out.bits.flag_crc := true.B
io.out.flag_crc.valid := true.B
io.data.ready := true.B
*/

//scala declaration(note: can be a class)
  //state parameter
  //val idle :: preamble :: aa :: pdu_header :: pdu_payload :: crc :: Nil = Enum(6)
  val io = IO(new PacketDisAssemblerIO)

  val reg_aa = "b01101011011111011001000101110001".U  
  val reg_crc_seed = "b010101010101010101010101".U
  val reg_dewhite_seed = "b1100101".U

  val idle = Wire(UInt(3.W))
  val preamble = Wire(UInt(3.W))
  val aa = Wire(UInt(3.W))
  val pdu_header = Wire(UInt(3.W))
  val pdu_payload = Wire(UInt(3.W))
  val crc = Wire(UInt(3.W))
  val wait_dma = Wire(UInt(3.W))
  idle := 0.U
  preamble := 1.U
  aa := 2.U
  pdu_header := 3.U
  pdu_payload := 4.U
  crc := 5.U
  wait_dma := 6.U

  val initial_state = idle
  val state_list = List(idle, preamble, aa, pdu_header, pdu_payload, crc, wait_dma)

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
  val flag_aa_r = RegInit(Bool(), false.B)
  val flag_aa_valid_r = RegInit(Bool(), false.B)
  val flag_crc_r = RegInit(Bool(), false.B)
  val flag_crc_valid_r = RegInit(Bool(), false.B)

  //Preamble
  val preamble0 = Wire(UInt(8.W))
  val preamble1 = Wire(UInt(8.W))
  val preamble01 = Wire(UInt(8.W))

  //DMA_Data
  val dma_data_valid_r = RegInit(Bool(), false.B)
  val dma_data_fire_w = Wire(Bool())

  //AFIFO
  val afifo_ready_r = RegInit(Bool(), false.B)
  val afifo_fire_w = Wire(Bool())

  //data registers
  //val data_w = Wire(UInt(8.W))
  val data_w = Wire(Vec(8, Bool()))
  val data_r = RegInit(UInt(8.W), 0.U)

  //crc
  val crc_Reset_w = Wire(Bool())
  val crc_Data_w = Wire(UInt(1.W))
  val crc_Valid_w = Wire(Bool())
  val crc_Result_w = Wire(UInt(24.W))
  val crc_Seed_w = Wire(UInt(24.W))

  //whitening
  val dewhite_Reset_w = Wire(Bool())
  val dewhite_Data_w = Wire(UInt(1.W))
  val dewhite_Valid_w = Wire(Bool())  
  val dewhite_Result_w = Wire(UInt(1.W))
  val dewhite_Seed_w = Wire(UInt(7.W))      

  //assignments
    //Decouple firing
  dma_data_fire_w := io.out.data.ready & io.out.data.valid 
  afifo_fire_w := io.in.data.ready & io.in.data.valid
  //PDU_Length_Fire_w := io.out.length.ready & io.out.length.valid
  //Flag_aa_Fire_w := io.out.flag_aa.ready & io.out.flag_aa.valid
  //Flag_crc_Fire_w := io.out.flag_crc.ready & io.out.flag_crc.valid

    //preamble hard code
  preamble0 := "b10101010".U
  preamble1 := "b01010101".U
  when(reg_aa(0) === 0.U){
    preamble01 := preamble0    
  }.otherwise{
    preamble01 := preamble1     
  }


  //output function
  when(state_r === idle || state_r === preamble){
    io.out.bits.data := 0.U
  }.otherwise{//aa, pdu_header, pdu_payload, crc
    io.out.bits.data := data_r 
  }

  io.out.bits.length := PDU_Length_r
  io.out.length.valid := PDU_Length_Valid_r
  io.out.bits.flag_aa := flag_aa_r
  io.out.flag_aa.valid := flag_aa_valid_r
  io.out.bits.flag_crc := flag_crc_r
  io.out.flag_crc.valid := flag_crc_valid_r

  io.out.data.valid := dma_data_valid_r
  io.in.data.ready := afifo_ready_r

  //default
  state_w      := state_r
  counter_w    := counter_r
  counter_byte_w := counter_byte_r
  data_w     := data_r.toBools

  PDU_Length_r   := PDU_Length_r//note: preserve value
  PDU_Length_Valid_r := PDU_Length_Valid_r 
  flag_aa_r := flag_aa_r
  flag_aa_valid_r := flag_aa_valid_r
  flag_crc_r := flag_crc_r
  flag_crc_valid_r := flag_crc_valid_r 

  dma_data_valid_r := dma_data_valid_r
  afifo_ready_r  := afifo_ready_r

  //StateTransition with counter updates
    //Note: counter_r updates when dma_data_fire_w
    //Note: counter_byte_r updates when afifo_fire_w
  when(state_r === idle){
    PDU_Length_r := 0.U
    PDU_Length_Valid_r := false.B
    flag_aa_r := false.B
    flag_aa_valid_r := false.B
    flag_crc_r := false.B
    flag_crc_valid_r := false.B
    when(io.in.switch === true.B){//note: switch usage
      state_w := preamble
    }.otherwise{
      state_w := idle
    }
  }.elsewhen(state_r === preamble){
    when(data_w.asUInt === preamble01){//note: data_w or data_r
      state_w := aa
      counter_w := 0.U
      counter_byte_w := 0.U
    }.otherwise{
      state_w := preamble
    }   
  }.elsewhen(state_r === aa){
    when(counter_r === 3.U && dma_data_fire_w === true.B){//note
      state_w := pdu_header
      counter_w := 0.U
      counter_byte_w := 0.U
    }.otherwise{
      state_w := aa
      when(dma_data_fire_w === true.B){
        counter_w := counter_r+1.U     
      }
      when(afifo_fire_w === true.B){
        when(counter_byte_r === 7.U){
          counter_byte_w := 0.U
        }.otherwise{
          counter_byte_w := counter_byte_r+1.U
        }
      }       
    }     
  }.elsewhen(state_r === pdu_header){
    when(counter_r === 1.U && dma_data_fire_w === true.B){//note
      state_w := pdu_payload
      counter_w := 0.U
      counter_byte_w := 0.U
    }.otherwise{
      state_w := pdu_header
      when(dma_data_fire_w === true.B){
        counter_w := counter_r+1.U     
      }
      when(afifo_fire_w === true.B){
        when(counter_byte_r === 7.U){
          counter_byte_w := 0.U
        }.otherwise{
          counter_byte_w := counter_byte_r+1.U
        }
      }       
    }     
  }.elsewhen(state_r === pdu_payload){
    when(counter_r === PDU_Length_r-1.U && dma_data_fire_w === true.B){//note
      state_w := crc
      counter_w := 0.U
      counter_byte_w := 0.U
    }.otherwise{
      state_w := pdu_payload
      when(dma_data_fire_w === true.B){
        counter_w := counter_r+1.U     
      }
      when(afifo_fire_w === true.B){
        when(counter_byte_r === 7.U){
          counter_byte_w := 0.U
        }.otherwise{
          counter_byte_w := counter_byte_r+1.U
        }
      }         
    }     
  }.elsewhen(state_r === crc){
    when(counter_r === 2.U && dma_data_fire_w === true.B){//note
      state_w := wait_dma
      counter_w := 0.U
      counter_byte_w := 0.U
    }.otherwise{
      state_w := crc
      when(dma_data_fire_w === true.B){
        counter_w := counter_r+1.U     
      }
      when(afifo_fire_w === true.B){
        when(counter_byte_r === 7.U){
          counter_byte_w := 0.U
        }.otherwise{
          counter_byte_w := counter_byte_r+1.U
        }
      }       
    }   
  }.elsewhen(state_r === wait_dma) {
    when (io.out.length.ready === true.B && io.out.flag_aa.ready === true.B && io.out.flag_crc.ready === true.B) {
      state_w := idle
    }.otherwise {
      state_w := wait_dma
    }
  }.otherwise{
    state_w := idle//error
  }

    //PDU_Length
  //when(state_r === pdu_payload && counter_r === 0.U && counter_byte_r === 0.U){//note: can change to intuitive statement(add fire_w) with data_w
  when(state_r === pdu_header && counter_r === 1.U && dma_data_fire_w === true.B){//note: can change to intuitive statement(add fire_w) with data_w
    PDU_Length_r := data_r
    PDU_Length_Valid_r := true.B
  }.elsewhen(state_w === idle) {
    PDU_Length_Valid_r := false.B
  }.otherwise{
    //do nothing: registers preserve value//note
  }

    //Flag_aa
  when(state_r === aa && counter_r === 0.U && dma_data_fire_w === true.B){//note: same as above
    when(data_r =/= reg_aa(7,0)){
      flag_aa_r := true.B
      flag_aa_valid_r := true.B      
    }
  }.elsewhen(state_r === aa && counter_r === 1.U && dma_data_fire_w === true.B){
    when(data_r =/= reg_aa(15,8)){
      flag_aa_r := true.B
      flag_aa_valid_r := true.B      
    }    
  }.elsewhen(state_r === aa && counter_r === 2.U && dma_data_fire_w === true.B){
    when(data_r =/= reg_aa(23,16)){
      flag_aa_r := true.B
      flag_aa_valid_r := true.B      
    }
  }.elsewhen(state_r === aa && counter_r === 3.U && dma_data_fire_w === true.B){
    when(data_r =/= reg_aa(31,24)){
      flag_aa_r := true.B
      flag_aa_valid_r := true.B      
    }.otherwise{
      flag_aa_valid_r := true.B        
    }
  }.otherwise{
    //do nothing: registers preserve value//note
  }

    //Flag_crc
  when(state_r === crc && counter_r === 0.U && dma_data_fire_w === true.B){//note: same as above
    when(data_r =/= crc_Result_w(7,0)){
      flag_crc_r := true.B
      flag_crc_valid_r := true.B      
    }
  }.elsewhen(state_r === crc && counter_r === 1.U && dma_data_fire_w === true.B){
    when(data_r =/= crc_Result_w(15,8)){
      flag_crc_r := true.B
      flag_crc_valid_r := true.B      
    }   
  }.elsewhen(state_r === crc && counter_r === 2.U && dma_data_fire_w === true.B){
    when(data_r =/= crc_Result_w(23,16)){
      flag_crc_r := true.B
      flag_crc_valid_r := true.B      
    }.otherwise{
      flag_crc_valid_r := true.B        
    }
  }.otherwise{
    //do nothing: registers preserve value//note
  }


  //dma_data_valid_r//note:check corner cases
  when(state_r === idle || state_r === preamble){
    dma_data_valid_r := false.B
  }.otherwise{//aa, pdu_header, pdu_payload, crc
    when(counter_byte_r === 7.U && afifo_fire_w === true.B){
      dma_data_valid_r := true.B
    }.elsewhen(dma_data_fire_w === true.B){
      dma_data_valid_r := false.B
    }
  }

  //AFIFO_Ready_w//note:check corner cases
  when(state_r === idle){
    afifo_ready_r := false.B
  }.elsewhen(state_r === preamble){
    afifo_ready_r := true.B
  }.otherwise{//aa, pdu_header, pdu_payload, crc
    when(counter_byte_r === 7.U && afifo_fire_w === true.B){
      afifo_ready_r := false.B     
    }.elsewhen(dma_data_fire_w === true.B){
      afifo_ready_r := true.B        
    }
  }

  //data
  when(state_r === pdu_header || state_r === pdu_payload || state_r === crc){
    when(afifo_fire_w === true.B){
      //data_w(counter_byte_r) := dewhite_Result_w.toBools
      when(dewhite_Result_w===0.U){
        data_w(counter_byte_r) := false.B
      }.otherwise{
        data_w(counter_byte_r) := true.B
      }
    }
  }.elsewhen(state_r === preamble){
    when(afifo_fire_w === true.B){
      //data_w(7) := io.data.bits.toBools //note: subword assignment
      when(io.in.bits.data===0.U){
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
  }.elsewhen(state_r === aa){
    when(afifo_fire_w === true.B){
      //data_w(counter_byte_r) := io.data.bits.toBools
      when(io.in.bits.data===0.U){
        data_w(counter_byte_r) := false.B
      }.otherwise{
        data_w(counter_byte_r) := true.B
      }
    }
  }.otherwise{//idle
    //do nothing or := 0.U
  }

  //crc
  crc_Reset_w := (state_r === idle)
  when(state_r === pdu_header || state_r === pdu_payload){//check corner cases
    crc_Data_w := dewhite_Result_w
    crc_Valid_w := afifo_fire_w
  }.otherwise{
    crc_Data_w := 0.U
    crc_Valid_w := false.B
  }
  //crc_Result_w wires to crc module
  crc_Seed_w := reg_crc_seed

  //dewhitening
  dewhite_Reset_w := (state_r === idle)
  when(state_r === pdu_header || state_r === pdu_payload || state_r === crc){//check corner cases  
    dewhite_Data_w  := io.in.bits.data
    dewhite_Valid_w := afifo_fire_w
  }.otherwise{
    dewhite_Data_w  := 0.U
    dewhite_Valid_w := false.B
  }
  //dewhite_Result_w wires to WHITE module
  dewhite_Seed_w := reg_dewhite_seed

  //sequential logic
  state_r      := state_w
  counter_r    := counter_w
  counter_byte_r := counter_byte_w
  data_r     := data_w.asUInt

  //crc instantiate
  val crc_inst = Module(new Serial_CRC)

  crc_inst.io.init := crc_Reset_w
  crc_inst.io.operand.bits := crc_Data_w
  crc_inst.io.operand.valid := crc_Valid_w
  crc_Result_w := crc_inst.io.result.bits
  crc_inst.io.result.ready := true.B
  crc_inst.io.seed := crc_Seed_w

  //whitening instantiate
  val WHITE_inst = Module(new Whitening)

  WHITE_inst.io.init := dewhite_Reset_w
  WHITE_inst.io.operand.bits := dewhite_Data_w
  WHITE_inst.io.operand.valid := dewhite_Valid_w
  dewhite_Result_w := WHITE_inst.io.result.bits
  WHITE_inst.io.result.ready := true.B
  WHITE_inst.io.seed := dewhite_Seed_w

/*
//for testing
  //crc instantiate
  val crc_inst = Module(new crc_TestModule)

  crc_inst.io.init := crc_Reset_w
  crc_inst.io.operand.bits := crc_Data_w
  crc_inst.io.operand.valid := crc_Valid_w
  crc_Result_w := crc_inst.io.result
  crc_inst.io.seed := crc_Seed_w

  //whitening instantiate
  val WHITE_inst = Module(new Whitening_TestModule)

  WHITE_inst.io.init := dewhite_Reset_w
  WHITE_inst.io.operand.bits := dewhite_Data_w
  WHITE_inst.io.operand.valid := dewhite_Valid_w
  dewhite_Result_w := WHITE_inst.io.result
  WHITE_inst.io.seed := dewhite_Seed_w
*/
}
