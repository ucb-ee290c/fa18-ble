package PacketAssembler//note

import chisel3._
import chisel3.util._
import CRC._
import Whitening._

class PDABundle extends Bundle {

  val switch = Input(Bool())
  val aa = Input(UInt(32.W))
  val crc_seed = Input(UInt(24.W))
  val dewhite_seed = Input(UInt(7.W))

  val data = DecoupledIO(UInt(8.W)) // decouple(sink): data, push, full
  val payload_length = Decoupled(UInt(8.W))
  val flag_aa = Decoupled(Bool())
  val flag_crc = Decoupled(Bool())

  override def cloneType: this.type = PDABundle.asInstanceOf[this.type]
}

object PDABundle {
  def apply = new PDABundle
}

class PacketDisassemblerIO extends Bundle {
  val in = Flipped(Decoupled(UInt(1.W)))
  val out = new PDABundle
}


class PacketDisAssembler extends Module {
  val io = IO(new PacketDisassemblerIO)

  val idle :: preamble :: aa :: pdu_header :: pdu_payload :: crc :: wait_dma:: nil = Enum(7)
  val state = RegInit(idle)

  val counter = RegInit(0.U(8.W))
  val counter_byte = RegInit(0.U(3.W))

  //packet status
  val pdu_length = RegInit(0.U(8.W))
  val pdu_length_valid = RegInit(false.B)
  val flag_aa = RegInit(false.B)
  val flag_aa_valid = RegInit(false.B)
  val flag_crc = RegInit(false.B)
  val flag_crc_valid = RegInit(false.B)

  //preamble
  val preamble0 = "b10101010".U
  val preamble1 = "b01010101".U
  val preamble01 = Wire(UInt(8.W))

  when(io.out.aa(0) === 0.U){
    preamble01 := preamble0
  }.otherwise{
    preamble01 := preamble1
  }  

  val in_data_ready = RegInit(false.B)
  val out_data_valid = RegInit(false.B)

  //data registers
  val data = RegInit(Vec.fill(8)(false.B))

  //crc
  val crc_reset = (state === idle)
  val crc_data = Wire(UInt(1.W))
  val crc_valid = Wire(Bool())
  val crc_result = Wire(UInt(24.W))
  val crc_seed = io.out.crc_seed

  //whitening
  val dewhite_reset = (state === idle)
  val dewhite_data = Wire(UInt(1.W))
  val dewhite_valid = Wire(Bool())
  val dewhite_result = Wire(UInt(1.W))
  val dewhite_seed = io.out.dewhite_seed

  //input, output ready/valid

  io.out.payload_length.bits := pdu_length
  io.out.payload_length.valid := pdu_length_valid
  io.out.flag_aa.bits := flag_aa
  io.out.flag_aa.valid := flag_aa_valid
  io.out.flag_crc.bits := flag_crc
  io.out.flag_crc.valid := flag_crc_valid

  io.out.data.valid := out_data_valid
  io.in.ready := in_data_ready

  //output bits
  when(state === idle || state === preamble){
    io.out.data.bits := 0.U
  }.otherwise{//aa, pdu_header, pdu_payload, crc
    io.out.data.bits := data.asUInt
  }

  //statetransition with counter updates
    //note: counter updates when io.out.data.fire()
    //note: counter_byte updates when io.in.fire()
  when(state === idle){
    pdu_length := 0.U
    pdu_length_valid := false.B
    flag_aa := false.B
    flag_aa_valid := false.B
    flag_crc := false.B
    flag_crc_valid := false.B
    when(io.out.switch === true.B){ // note: dma_switch_i usage
      state := preamble
    }.otherwise{
      state := idle
    }
  }.elsewhen(state === preamble){
    when(data.asUInt === preamble01){
      state := aa
      counter := 0.U
      counter_byte := 0.U
    }.otherwise{
      state := preamble
    }
  }.elsewhen(state === aa){
    when(counter === 3.U && io.out.data.fire()){//note
      state := pdu_header
      counter := 0.U
      counter_byte := 0.U
    }.otherwise{
      state := aa
      when(io.out.data.fire()){
        counter := counter+1.U
      }
      when(io.in.fire()){
        when(counter_byte === 7.U){
          counter_byte := 0.U
        }.otherwise{
          counter_byte := counter_byte+1.U
        }
      }
    }
  }.elsewhen(state === pdu_header){
    when(counter === 1.U && io.out.data.fire()){//note
      state := pdu_payload
      counter := 0.U
      counter_byte := 0.U
    }.otherwise{
      state := pdu_header
      when(io.out.data.fire()){
        counter := counter+1.U
      }
      when(io.in.fire()){
        when(counter_byte === 7.U){
          counter_byte := 0.U
        }.otherwise{
          counter_byte := counter_byte+1.U
        }
      }
    }
  }.elsewhen(state === pdu_payload){
    when(counter === pdu_length - 1.U && io.out.data.fire()){//note
      state := crc
      counter := 0.U
      counter_byte := 0.U
    }.otherwise{
      state := pdu_payload
      when(io.out.data.fire()){
        counter := counter+1.U
      }
      when(io.in.fire()){
        when(counter_byte === 7.U){
          counter_byte := 0.U
        }.otherwise{
          counter_byte := counter_byte+1.U
        }
      }
    }
  }.elsewhen(state === crc){
    when(counter === 2.U && io.out.data.fire()){
      state := wait_dma
      counter := 0.U
      counter_byte := 0.U
    }.otherwise{
      state := crc
      when(io.out.data.fire() === true.B){
        counter := counter + 1.U
      }
      when(io.in.fire()){
        when(counter_byte === 7.U){
          counter_byte := 0.U
        }.otherwise{
          counter_byte := counter_byte + 1.U
        }
      }
    }
  }.elsewhen(state === wait_dma) {
    when (io.out.payload_length.ready === true.B && io.out.flag_aa.ready === true.B && io.out.flag_crc.ready === true.B) {
      state := idle
    }.otherwise {
      state := wait_dma
    }
  }.otherwise{
    state := idle//error
  }

    //pdu_length
  when(state === pdu_header && counter === 1.U && io.out.data.fire() === true.B){//note: can change to intuitive statement(add fire_w) with data
    pdu_length := data.asUInt
    pdu_length_valid := true.B
  }.elsewhen(state === idle) {
    pdu_length_valid := false.B
  }.otherwise{
    //do nothing: registers preserve value
  }

    //flag_aa   
  when(state === aa && counter === 0.U && io.out.data.fire() === true.B){//note: same as above
    when(data.asUInt =/= io.out.aa(7,0)){
      flag_aa := true.B
      flag_aa_valid := true.B
    }
  }.elsewhen(state === aa && counter === 1.U && io.out.data.fire() === true.B){
    when(data.asUInt =/= io.out.aa(15,8)){
      flag_aa := true.B
      flag_aa_valid := true.B
    }
  }.elsewhen(state === aa && counter === 2.U && io.out.data.fire() === true.B){
    when(data.asUInt =/= io.out.aa(23,16)){
      flag_aa := true.B
      flag_aa_valid := true.B
    }
  }.elsewhen(state === aa && counter === 3.U && io.out.data.fire() === true.B){
    when(data.asUInt =/= io.out.aa(31,24)){
      flag_aa := true.B
      flag_aa_valid := true.B
    }.otherwise{
      flag_aa_valid := true.B
    }
  }.otherwise{

  }

    //flag_crc
  when(state === crc && counter === 0.U && io.out.data.fire() === true.B){//note: same as above
    when(data.asUInt =/= crc_result(7,0)){
      flag_crc := true.B
      flag_crc_valid := true.B
    }
  }.elsewhen(state === crc && counter === 1.U && io.out.data.fire() === true.B){
    when(data.asUInt =/= crc_result(15,8)){
      flag_crc := true.B
      flag_crc_valid := true.B
    }
  }.elsewhen(state === crc && counter === 2.U && io.out.data.fire() === true.B){
    when(data.asUInt =/= crc_result(23,16)){
      flag_crc := true.B
      flag_crc_valid := true.B
    }.otherwise{
      flag_crc_valid := true.B
    }
  }.otherwise{
    //do nothing: registers preserve value
  }


  //out_data_valid //note:check corner cases
  when(state === idle || state === preamble){
    out_data_valid := false.B
  }.otherwise{//aa, pdu_header, pdu_payload, crc
    when(counter_byte === 7.U && io.in.fire()){
      out_data_valid := true.B
    }.elsewhen(io.out.data.fire()){
      out_data_valid := false.B
    }
  }

  //in_data_ready //note:check corner cases
  when(state === idle){
    in_data_ready := false.B
  }.elsewhen(state === preamble){
    in_data_ready := true.B
  }.otherwise{//aa, pdu_header, pdu_payload, crc
    when(counter_byte === 7.U && io.in.fire()){
      in_data_ready := false.B
    }.elsewhen(io.out.data.fire()){
      in_data_ready := true.B
    }
  }

  //data
  when(state === pdu_header || state === pdu_payload || state === crc){
    when(io.in.fire()){
      when(dewhite_result === 0.U)
      {
        data(counter_byte) := false.B
      }.otherwise{
        data(counter_byte) := true.B
      }
    }
  }.elsewhen(state === preamble){
    when(io.in.fire()){
      when(io.in.bits === 0.U){
        data(7) := false.B
      }.otherwise{
        data(7) := true.B
      }
      for(i<- 0 to 6){
        when(data(i+1) === 0.U){
          data(i) := false.B 
        }.otherwise{
          data(i) := true.B
        }
      }
    }
  }.elsewhen(state === aa){
    when(io.in.fire()){
      //data(counter_byte) := io.in.bits.toBools
      when(io.in.bits===0.U){
        data(counter_byte) := false.B
      }.otherwise{
        data(counter_byte) := true.B
      }
    }
  }.otherwise{//idle
    //do nothing or := 0.U
  }

  //crc
  when(state === pdu_header || state === pdu_payload){//check corner cases
    crc_data := dewhite_result
    crc_valid := io.in.fire()
  }.otherwise{
    crc_data := 0.U
    crc_valid := false.B
  }

  //dewhitening
  when(state === pdu_header || state === pdu_payload || state === crc){//check corner cases
    dewhite_data  := io.in.bits
    dewhite_valid := io.in.fire()
  }.otherwise{
    dewhite_data  := 0.U
    dewhite_valid := false.B
  }

  //crc instantiate
  val crc_inst = Module(new Serial_CRC)

  crc_inst.io.init := crc_reset
  crc_inst.io.operand.bits := crc_data
  crc_inst.io.operand.valid := crc_valid
  crc_result := crc_inst.io.result.bits
  crc_inst.io.result.ready := true.B
  crc_inst.io.seed := crc_seed

  //whitening instantiate
  val white_inst = Module(new Whitening)

  white_inst.io.init := dewhite_reset
  white_inst.io.operand.bits := dewhite_data
  white_inst.io.operand.valid := dewhite_valid
  dewhite_result := white_inst.io.result.bits
  white_inst.io.result.ready := true.B
  white_inst.io.seed := dewhite_seed
}
