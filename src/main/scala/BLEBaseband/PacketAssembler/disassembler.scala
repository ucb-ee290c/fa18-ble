package PacketDisAssembler//note

import chisel3._
import chisel3.Util._
import CRC._
import Whitening._

class PDABundle extends Bundle {

  //dma, reg
  val dma_switch_i = Input(Bool())
  val reg_aa_i = Input(UInt(32.W))
  val reg_crc_seed_i = Input(UInt(24.W))
  val reg_dewhite_seed_i = Input(UInt(7.W))

  val dma_data_o = DecoupledIO(UInt(8.W)) // decouple(sink): data, push, full
  val dma_length_o = Decoupled(UInt(8.W))
  val dma_flag_aa_o = Decoupled(Bool())
  val dma_flag_crc_o = Decoupled(Bool())

  //async fifo
  val afifo_data_i = Flipped(DecoupledIO(UInt(1.W)))  // decouple(source): data, pop, empty

  override def cloneType: this.type = PDABundle.asInstanceOf[this.type]
}

object PDABundle {
  def apply = new PDABundle
}

class PacketDisAssemblerio extends Bundle {
  val in = new PDABundle
  val out = Decoupled(UInt(1.W))
}


class PacketDisAssembler extends Module {
  val io = io(PacketDisAssemblerIO)

/*
//testing assignments
io.dma_data_o.bits := 0.U
io.dma_data_o.valid := true.B
io.dma_length_o.bits := 0.U
io.dma_length_o.valid := true.B
io.dma_flag_aa_o.bits := true.B
io.dma_flag_aa_o.valid := true.B
io.dma_flag_crc_o.bits := true.B
io.dma_flag_crc_o.valid := true.B
io.afifo_data_i.ready := true.B
*/

//scala declaration(note: can be a class)
  //state parameter
  val idle :: preamble :: aa :: pdu_header :: pdu_payload :: crc :: wait_dma:: nil = Enum(7)
  val state = RegInit(idle)

  //reg, Wire
  //fsm
  val state_w =  Wire(UInt(3.W))
  val state_r = RegInit(UInt(3.W), idle)

  val counter_w = Wire(UInt(8.W))//at most 255 for pdu
  val counter_r = RegInit(UInt(8.W), 0.U)

  val counter_byte_w = Wire(UInt(3.W))//bit in byte out
  val counter_byte_r = RegInit(UInt(3.W), 0.U)

  //packet status
  val pdu_length_r = RegInit(UInt(8.W), 0.U)
  val pdu_length_valid_r = RegInit(Bool(), false.B)
  val flag_aa_r = RegInit(Bool(), false.B)
  val flag_aa_valid_r = RegInit(Bool(), false.B)
  val flag_crc_r = RegInit(Bool(), false.B)
  val flag_crc_valid_r = RegInit(Bool(), false.B)

  //preamble
  val preamble0 = Wire(UInt(8.W))
  val preamble1 = Wire(UInt(8.W))
  val preamble01 = Wire(UInt(8.W))

  //dma_data
  val dma_data_valid_r = RegInit(Bool(), false.B)
  val dma_data_fire_w = Wire(Bool())

  //afifo
  val afifo_ready_r = RegInit(Bool(), false.B)
  val afifo_fire_w = Wire(Bool())

  //data registers
  //val data_w = Wire(UInt(8.W))
  val data_w = Wire(vec(8, Bool()))
  val data_r = RegInit(UInt(8.W), 0.U)

  //crc
  val crc_reset_w = Wire(Bool())
  val crc_data_w = Wire(UInt(1.W))
  val crc_valid_w = Wire(Bool())
  val crc_result_w = Wire(UInt(24.W))
  val crc_seed_w = Wire(UInt(24.W))

  //whitening
  val dewhite_reset_w = Wire(Bool())
  val dewhite_data_w = Wire(UInt(1.W))
  val dewhite_valid_w = Wire(Bool())
  val dewhite_result_w = Wire(UInt(1.W))
  val dewhite_seed_w = Wire(UInt(7.W))

  //assignments
    //decouple firing
  dma_data_fire_w := io.dma_data_o.ready & io.dma_data_o.valid
  afifo_fire_w := io.afifo_data_i.ready & io.afifo_data_i.valid
  //pdu_length_fire_w := io.dma_length_o.ready & io.dma_length_o.valid
  //flag_aa_fire_w := io.dma_flag_aa_o.ready & io.dma_flag_aa_o.valid
  //flag_crc_fire_w := io.dma_flag_crc_o.ready & io.dma_flag_crc_o.valid

    //preamble hard code
  preamble0 := "b10101010".U
  preamble1 := "b01010101".U
  when(io.reg_aa_i(0) === 0.U){
    preamble01 := preamble0
  }.otherwise{
    preamble01 := preamble1
  }


  //output function
  when(state_r === idle || state_r === preamble){
    io.dma_data_o.bits := 0.U
  }.otherwise{//aa, pdu_header, pdu_payload, crc
    io.dma_data_o.bits := data_r
  }

  io.dma_length_o.bits := pdu_length_r
  io.dma_length_o.valid := pdu_length_valid_r
  io.dma_flag_aa_o.bits := flag_aa_r
  io.dma_flag_aa_o.valid := flag_aa_valid_r
  io.dma_flag_crc_o.bits := flag_crc_r
  io.dma_flag_crc_o.valid := flag_crc_valid_r

  io.dma_data_o.valid := dma_data_valid_r
  io.afifo_data_i.ready := afifo_ready_r

  //default
  state_w      := state_r
  counter_w    := counter_r
  counter_byte_w := counter_byte_r
  data_w     := data_r.toBools

  pdu_length_r   := pdu_length_r  // note: preserve value
  pdu_length_valid_r := pdu_length_valid_r
  flag_aa_r := flag_aa_r
  flag_aa_valid_r := flag_aa_valid_r
  flag_crc_r := flag_crc_r
  flag_crc_valid_r := flag_crc_valid_r

  dma_data_valid_r := dma_data_valid_r
  afifo_ready_r  := afifo_ready_r

  //statetransition with counter updates
    //note: counter_r updates when dma_data_fire_w
    //note: counter_byte_r updates when afifo_fire_w
  when(state_r === idle){
    pdu_length_r := 0.U
    pdu_length_valid_r := false.B
    flag_aa_r := false.B
    flag_aa_valid_r := false.B
    flag_crc_r := false.B
    flag_crc_valid_r := false.B
    when(io.dma_switch_i === true.B){ // note: dma_switch_i usage
      state_w := preamble
    }.otherwise{
      state_w := idle
    }
  }.elsewhen(state_r === preamble){
    when(data_w.asUInt === preamble01){ // note: data_w or data_r
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
    when(counter_r === pdu_length_r-1.U && dma_data_fire_w === true.B){//note
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
    when(counter_r === 2.U && dma_data_fire_w === true.B){
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
    when (io.dma_length_o.ready === true.B && io.dma_flag_aa_o.ready === true.B && io.dma_flag_crc_o.ready === true.B) {
      state_w := idle
    }.otherwise {
      state_w := wait_dma
    }
  }.otherwise{
    state_w := idle//error
  }

    //pdu_length
  when(state_r === pdu_header && counter_r === 1.U && dma_data_fire_w === true.B){//note: can change to intuitive statement(add fire_w) with data_w
    pdu_length_r := data_r
    pdu_length_valid_r := true.B
  }.elsewhen(state_w === idle) {
    pdu_length_valid_r := false.B
  }.otherwise{
    //do nothing: registers preserve value
  }

    //flag_aa
  when(state_r === aa && counter_r === 0.U && dma_data_fire_w === true.B){//note: same as above
    when(data_r =/= io.reg_aa_i(7,0)){
      flag_aa_r := true.B
      flag_aa_valid_r := true.B
    }
  }.elsewhen(state_r === aa && counter_r === 1.U && dma_data_fire_w === true.B){
    when(data_r =/= io.reg_aa_i(15,8)){
      flag_aa_r := true.B
      flag_aa_valid_r := true.B
    }
  }.elsewhen(state_r === aa && counter_r === 2.U && dma_data_fire_w === true.B){
    when(data_r =/= io.reg_aa_i(23,16)){
      flag_aa_r := true.B
      flag_aa_valid_r := true.B
    }
  }.elsewhen(state_r === aa && counter_r === 3.U && dma_data_fire_w === true.B){
    when(data_r =/= io.reg_aa_i(31,24)){
      flag_aa_r := true.B
      flag_aa_valid_r := true.B
    }.otherwise{
      flag_aa_valid_r := true.B
    }
  }.otherwise{
    //do nothing: registers preserve value
  }

    //flag_crc
  when(state_r === crc && counter_r === 0.U && dma_data_fire_w === true.B){//note: same as above
    when(data_r =/= crc_result_w(7,0)){
      flag_crc_r := true.B
      flag_crc_valid_r := true.B
    }
  }.elsewhen(state_r === crc && counter_r === 1.U && dma_data_fire_w === true.B){
    when(data_r =/= crc_result_w(15,8)){
      flag_crc_r := true.B
      flag_crc_valid_r := true.B
    }
  }.elsewhen(state_r === crc && counter_r === 2.U && dma_data_fire_w === true.B){
    when(data_r =/= crc_result_w(23,16)){
      flag_crc_r := true.B
      flag_crc_valid_r := true.B
    }.otherwise{
      flag_crc_valid_r := true.B
    }
  }.otherwise{
    //do nothing: registers preserve value
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

  //afifo_ready_w //note:check corner cases
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
      //data_w(counter_byte_r) := dewhite_result_w.toBools
      when(dewhite_result_w===0.U){
        data_w(counter_byte_r) := false.B
      }.otherwise{
        data_w(counter_byte_r) := true.B
      }
    }
  }.elsewhen(state_r === preamble){
    when(afifo_fire_w === true.B){
      //data_w(7) := io.afifo_data_i.bits.toBools //note: subword assignment
      when(io.afifo_data_i.bits===0.U){
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
      //data_w(counter_byte_r) := io.afifo_data_i.bits.toBools
      when(io.afifo_data_i.bits===0.U){
        data_w(counter_byte_r) := false.B
      }.otherwise{
        data_w(counter_byte_r) := true.B
      }
    }
  }.otherwise{//idle
    //do nothing or := 0.U
  }

  //crc
  crc_reset_w := (state_r === idle)
  when(state_r === pdu_header || state_r === pdu_payload){//check corner cases
    crc_data_w := dewhite_result_w
    crc_valid_w := afifo_fire_w
  }.otherwise{
    crc_data_w := 0.U
    crc_valid_w := false.B
  }
  //crc_result_w Wires to crc Module
  crc_seed_w := io.reg_crc_seed_i

  //dewhitening
  dewhite_reset_w := (state_r === idle)
  when(state_r === pdu_header || state_r === pdu_payload || state_r === crc){//check corner cases
    dewhite_data_w  := io.afifo_data_i.bits
    dewhite_valid_w := afifo_fire_w
  }.otherwise{
    dewhite_data_w  := 0.U
    dewhite_valid_w := false.B
  }
  //dewhite_result_w Wires to white Module
  dewhite_seed_w := io.reg_dewhite_seed_i

  //sequential logic
  state_r      := state_w
  counter_r    := counter_w
  counter_byte_r := counter_byte_w
  data_r     := data_w.asUInt

  //crc instantiate
  val crc_inst = Module(new Serial_CRC)

  crc_inst.io.init := crc_reset_w
  crc_inst.io.operand.bits := crc_data_w
  crc_inst.io.operand.valid := crc_valid_w
  crc_result_w := crc_inst.io.result.bits
  crc_inst.io.result.ready := true.B
  crc_inst.io.seed := crc_seed_w

  //whitening instantiate
  val white_inst = Module(new Whitening)

  white_inst.io.init := dewhite_reset_w
  white_inst.io.operand.bits := dewhite_data_w
  white_inst.io.operand.valid := dewhite_valid_w
  dewhite_result_w := white_inst.io.result.bits
  white_inst.io.result.ready := true.B
  white_inst.io.seed := dewhite_seed_w

/*
//for testing
  //crc instantiate
  val crc_inst = Module(new crc_testModule)

  crc_inst.io.init := crc_reset_w
  crc_inst.io.operand.bits := crc_data_w
  crc_inst.io.operand.valid := crc_valid_w
  crc_result_w := crc_inst.io.result
  crc_inst.io.seed := crc_seed_w

  //whitening instantiate
  val white_inst = Module(new whitening_testModule)

  white_inst.io.init := dewhite_reset_w
  white_inst.io.operand.bits := dewhite_data_w
  white_inst.io.operand.valid := dewhite_valid_w
  dewhite_result_w := white_inst.io.result
  white_inst.io.seed := dewhite_seed_w
*/
}
