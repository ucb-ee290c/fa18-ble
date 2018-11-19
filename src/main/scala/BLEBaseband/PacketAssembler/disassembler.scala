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
	val length_valid = Output(Bool())
    	val flag_aa = Output(Bool())
	val flag_aa_valid = Output(Bool())
    	val flag_crc = Output(Bool()) 
	val flag_crc_valid = Output(Bool())

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
	def apply(): PacketDisAssemblerIO = new PacketDisAssemblerIO	
}

trait HasPeripheryPDA extends BaseSubsystem {
  // instantiate cordic chain
  val pdaChain = LazyModule(new PDAThing)
  // connect memory interfaces to pbus
  pbus.toVariableWidthSlave(Some("pdaWrite")) { pdaChain.writeQueue.mem.get }
  pbus.toVariableWidthSlave(Some("pdaRead")) { pdaChain.readQueue.mem.get }
}

class PacketDisAssembler extends Module {
    val io = IO(new PacketDisAssemblerIO)
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
io.out.bits.length_valid := true.B
io.out.bits.flag_aa := true.B
io.out.bits.flag_aa_valid := true.B
io.out.bits.flag_crc := true.B
io.out.bits.flag_crc_valid := true.B
io.data.ready := true.B
*/

//scala declaration(note: can be a class)
  //state parameter
  val idle :: preamble :: aa :: pdu_header :: pdu_payload :: crc :: wait_dma :: Nil = Enum(7)
  val state = RegInit(idle)

  val reg_aa = "b01101011011111011001000101110001".U  
  val reg_crc_seed = "b010101010101010101010101".U
  val reg_dewhite_seed = "b1100101".U

  /*val idle = Wire(UInt(3.W))
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
  */
  
  //reg, wire
  //FSM
  //val state = Wire(UInt(3.W))
  //val state = RegInit(UInt(3.W), initial_state)

  val counter = RegInit(0.U(8.W)) //counter for bytes in packet
  val counter_byte = RegInit(0.U(3.W)) //counter for bits in bytes

  //packet status
  val length = RegInit(0.U(8.W))
  val length_valid = RegInit(Bool(), false.B)
  val flag_aa = RegInit(false.B)
  val flag_aa_valid = RegInit(Bool(), false.B)
  val flag_crc = RegInit(false.B)
  val flag_crc_valid = RegInit(Bool(), false.B)

  //Preamble
  val preamble0 = Wire(UInt(8.W))
  val preamble1 = Wire(UInt(8.W))
  val preamble01 = Wire(UInt(8.W))

  //DMA_Data
  val dma_data_valid = RegInit(Bool(), false.B)
  val dma_data_fire = Wire(Bool())

  //AFIFO
  val afifo_ready = RegInit(Bool(), false.B)
  val afifo_fire = Wire(Bool())

  //data registers
  //val data = Wire(UInt(8.W))
  val data = RegInit(0.U(8.W))

  //crc
  val crc_reset = Wire(Bool())
  val crc_data = Wire(UInt(1.W))
  val crc_valid = Wire(Bool())
  val crc_result = Wire(UInt(24.W))
  val crc_seed = Wire(UInt(24.W))

  //whitening
  val dewhite_reset = Wire(Bool())
  val dewhite_data = Wire(UInt(1.W))
  val dewhite_valid = Wire(Bool())  
  val dewhite_result = Wire(UInt(1.W))
  val dewhite_seed = Wire(UInt(7.W))      

  //assignments
    //Decouple firing
  dma_data_fire := io.out.ready & io.out.valid 
  afifo_fire := io.in.ready & io.in.valid

    //preamble hard code
  preamble0 := "b10101010".U
  preamble1 := "b01010101".U
  when(reg_aa(0) === 0.U){
    preamble01 := preamble0    
  }.otherwise{
    preamble01 := preamble1     
  }


  //output function
  when(state === idle || state === preamble){
    io.out.bits.data := 0.U
  }.otherwise{//aa, pdu_header, pdu_payload, crc
    io.out.bits.data := data 
  }

  io.out.bits.length := length
  io.out.bits.length_valid := length_valid
  io.out.bits.flag_aa := flag_aa
  io.out.bits.flag_aa_valid := flag_aa_valid
  io.out.bits.flag_crc := flag_crc
  io.out.bits.flag_crc_valid := flag_crc_valid

  io.out.valid := dma_data_valid
  io.in.ready := afifo_ready

  //default
  dma_data_valid := dma_data_valid
  afifo_ready  := afifo_ready

  //StateTransition with counter updates
    //Note: counter updates when dma_data_fire
    //Note: counter_byte updates when afifo_fire
  when(state === idle){
    length := 0.U
    length_valid := false.B
    flag_aa := false.B
    flag_aa_valid := false.B
    flag_crc := false.B
    flag_crc_valid := false.B
    when(io.in.bits.switch === true.B){//note: switch usage
      state := preamble
    }.otherwise{
      state := idle
    }
  }.elsewhen(state === preamble){
    when(data.asUInt === preamble01){//note: data or data
      state := aa
      counter := 0.U
      counter_byte := 0.U
    }.otherwise{
      state := preamble
    }   
  }.elsewhen(state === aa){
    when(counter === 3.U && dma_data_fire === true.B){//note
      state := pdu_header
      counter := 0.U
      counter_byte := 0.U
    }.otherwise{
      state := aa
      when(dma_data_fire === true.B){
        counter := counter+1.U     
      }
      when(afifo_fire === true.B){
        when(counter_byte === 7.U){
          counter_byte := 0.U
        }.otherwise{
          counter_byte := counter_byte+1.U
        }
      }       
    }     
  }.elsewhen(state === pdu_header){
    when(counter === 1.U && dma_data_fire === true.B){//note
      state := pdu_payload
      counter := 0.U
      counter_byte := 0.U
    }.otherwise{
      state := pdu_header
      when(dma_data_fire === true.B){
        counter := counter+1.U     
      }
      when(afifo_fire === true.B){
        when(counter_byte === 7.U){
          counter_byte := 0.U
        }.otherwise{
          counter_byte := counter_byte+1.U
        }
      }       
    }     
  }.elsewhen(state === pdu_payload){
    when(counter === length-1.U && dma_data_fire === true.B){//note
      state := crc
      counter := 0.U
      counter_byte := 0.U
    }.otherwise{
      state := pdu_payload
      when(dma_data_fire === true.B){
        counter := counter+1.U     
      }
      when(afifo_fire === true.B){
        when(counter_byte === 7.U){
          counter_byte := 0.U
        }.otherwise{
          counter_byte := counter_byte+1.U
        }
      }         
    }     
  }.elsewhen(state === crc){
    when(counter === 2.U && dma_data_fire === true.B){//note
      state := wait_dma
      counter := 0.U
      counter_byte := 0.U
    }.otherwise{
      state := crc
      when(dma_data_fire === true.B){
        counter := counter+1.U     
      }
      when(afifo_fire === true.B){
        when(counter_byte === 7.U){
          counter_byte := 0.U
        }.otherwise{
          counter_byte := counter_byte+1.U
        }
      }       
    }   
  }.elsewhen(state === wait_dma) {
    when (io.out.ready === true.B) {
      state := idle
    }.otherwise {
      state := wait_dma
    }
  }.otherwise{
    state := idle//error
  }

    //PDU_Length
  //when(state === pdu_payload && counter === 0.U && counter_byte === 0.U){//note: can change to intuitive statement(add fire_w) with data
  when(state === pdu_header && counter === 1.U && dma_data_fire === true.B){//note: can change to intuitive statement(add fire_w) with data
    length := data
    length_valid := true.B
  }.elsewhen(state === idle) {
    length_valid := false.B
  }.otherwise{
    //do nothing: registers preserve value//note
  }

    //Flag_aa
  when(state === aa && counter === 0.U && dma_data_fire === true.B){//note: same as above
    when(data =/= reg_aa(7,0)){
      flag_aa := true.B
      flag_aa_valid := true.B      
    }
  }.elsewhen(state === aa && counter === 1.U && dma_data_fire === true.B){
    when(data =/= reg_aa(15,8)){
      flag_aa := true.B
      flag_aa_valid := true.B      
    }    
  }.elsewhen(state === aa && counter === 2.U && dma_data_fire === true.B){
    when(data =/= reg_aa(23,16)){
      flag_aa := true.B
      flag_aa_valid := true.B      
    }
  }.elsewhen(state === aa && counter === 3.U && dma_data_fire === true.B){
    when(data =/= reg_aa(31,24)){
      flag_aa := true.B
      flag_aa_valid := true.B      
    }.otherwise{
      flag_aa_valid := true.B        
    }
  }.otherwise{
    //do nothing: registers preserve value//note
  }

    //Flag_crc
  when(state === crc && counter === 0.U && dma_data_fire === true.B){//note: same as above
    when(data =/= crc_result(7,0)){
      flag_crc := true.B
      flag_crc_valid := true.B      
    }
  }.elsewhen(state === crc && counter === 1.U && dma_data_fire === true.B){
    when(data =/= crc_result(15,8)){
      flag_crc := true.B
      flag_crc_valid := true.B      
    }   
  }.elsewhen(state === crc && counter === 2.U && dma_data_fire === true.B){
    when(data =/= crc_result(23,16)){
      flag_crc := true.B
      flag_crc_valid := true.B      
    }.otherwise{
      flag_crc_valid := true.B        
    }
  }.otherwise{
    //do nothing: registers preserve value//note
  }


  //dma_data_valid//note:check corner cases
  when(state === idle || state === preamble){
    dma_data_valid := false.B
  }.otherwise{//aa, pdu_header, pdu_payload, crc
    when(counter_byte === 7.U && afifo_fire === true.B){
      dma_data_valid := true.B
    }.elsewhen(dma_data_fire === true.B){
      dma_data_valid := false.B
    }
  }

  //AFIFO_Ready_w//note:check corner cases
  when(state === idle){
    afifo_ready := false.B
  }.elsewhen(state === preamble){
    afifo_ready := true.B
  }.otherwise{//aa, pdu_header, pdu_payload, crc
    when(counter_byte === 7.U && afifo_fire === true.B){
      afifo_ready := false.B     
    }.elsewhen(dma_data_fire === true.B){
      afifo_ready := true.B        
    }
  }

  //data
  when(state === pdu_header || state === pdu_payload || state === crc){
    when(afifo_fire === true.B){
      //data(counter_byte) := dewhite_result.toBools
      when(dewhite_result===0.U){
        data(counter_byte) := false.B
      }.otherwise{
        data(counter_byte) := true.B
      }
    }
  }.elsewhen(state === preamble){
    when(afifo_fire === true.B){
      //data(7) := io.data.bits.toBools //note: subword assignment
      when(io.in.bits.data===0.U){
        data(7) := false.B
      }.otherwise{
        data(7) := true.B
      }
      for(i<-0 to 6){//value shifting
        //data(i) := data(i+1).toBools
        when(data(i+1)===0.U){
          data(i) := false.B
        }.otherwise{
          data(i) := true.B
        }
      }      
    }
  }.elsewhen(state === aa){
    when(afifo_fire === true.B){
      //data(counter_byte) := io.data.bits.toBools
      when(io.in.bits.data===0.U){
        data(counter_byte) := false.B
      }.otherwise{
        data(counter_byte) := true.B
      }
    }
  }.otherwise{//idle
    //do nothing or := 0.U
  }

  //crc
  crc_reset := (state === idle)
  when(state === pdu_header || state === pdu_payload){//check corner cases
    crc_data := dewhite_result
    crc_valid := afifo_fire
  }.otherwise{
    crc_data := 0.U
    crc_valid := false.B
  }
  //crc_result wires to crc module
  crc_seed := reg_crc_seed

  //dewhitening
  dewhite_reset := (state === idle)
  when(state === pdu_header || state === pdu_payload || state === crc){//check corner cases  
    dewhite_data  := io.in.bits.data
    dewhite_valid := afifo_fire
  }.otherwise{
    dewhite_data  := 0.U
    dewhite_valid := false.B
  }
  //dewhite_result wires to WHITE module
  dewhite_seed := reg_dewhite_seed

  //sequential logic
  state      := state
  counter    := counter
  counter_byte := counter_byte
  data     := data.asUInt

  //crc instantiate
  val crc_inst = Module(new Serial_CRC)

  crc_inst.io.init := crc_reset
  crc_inst.io.operand.bits := crc_data
  crc_inst.io.operand.valid := crc_valid
  crc_result := crc_inst.io.result.bits
  crc_inst.io.result.ready := true.B
  crc_inst.io.seed := crc_seed

  //whitening instantiate
  val WHITE_inst = Module(new Whitening)

  WHITE_inst.io.init := dewhite_reset
  WHITE_inst.io.operand.bits := dewhite_data
  WHITE_inst.io.operand.valid := dewhite_valid
  dewhite_result := WHITE_inst.io.result.bits
  WHITE_inst.io.result.ready := true.B
  WHITE_inst.io.seed := dewhite_seed

/*
//for testing
  //crc instantiate
  val crc_inst = Module(new crc_TestModule)

  crc_inst.io.init := crc_reset
  crc_inst.io.operand.bits := crc_data
  crc_inst.io.operand.valid := crc_valid
  crc_result := crc_inst.io.result
  crc_inst.io.seed := crc_seed

  //whitening instantiate
  val WHITE_inst = Module(new Whitening_TestModule)

  WHITE_inst.io.init := dewhite_reset
  WHITE_inst.io.operand.bits := dewhite_data
  WHITE_inst.io.operand.valid := dewhite_valid
  dewhite_result := WHITE_inst.io.result
  WHITE_inst.io.seed := dewhite_seed
*/
}
