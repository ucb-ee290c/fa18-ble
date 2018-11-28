package Loop//note

import chisel3._
import chisel3.util._
import CRC._
import Whitening._
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.subsystem.BaseSubsystem

class PDAInputBundle extends Bundle {
	    val switch = Output(Bool())
      val data = Output(UInt(1.W))//decouple(source): data, pop, empty

	override def cloneType: this.type = PDAInputBundle().asInstanceOf[this.type]
}

object PDAInputBundle {
	def apply(): PDAInputBundle = new PDAInputBundle
}

class PDAOutputBundle extends Bundle {
  val data = Output(UInt(8.W))//decouple(sink): data, push, full
  val length = Output(UInt(8.W))
	val length_valid = Output(Bool())
  val flag_aa = Output(Bool())
	val flag_aa_valid = Output(Bool())
  val flag_crc = Output(Bool())
	val flag_crc_valid = Output(Bool())
	val done = Output(Bool())


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
  val loopChain = LazyModule(new LoopThing)
  // connect memory interfaces to pbus
  pbus.toVariableWidthSlave(Some("pdaWrite")) { loopChain.writeQueue.mem.get }
  pbus.toVariableWidthSlave(Some("pdaRead")) { loopChain.readQueue.mem.get }
}

class PacketDisAssembler extends Module {
  
  val io = IO(new PacketDisAssemblerIO)

  val idle :: preamble :: aa :: pdu_header :: pdu_payload :: crc :: wait_dma :: Nil = Enum(7)
  val state = RegInit(idle)

  val reg_aa = "b10001110100010011011111011010110".U

  val counter = RegInit(0.U(8.W)) //counter for bytes in packet
  val counter_byte = RegInit(0.U(3.W)) //counter for bits in bytes

  //packet status
  val length = RegInit(0.U(8.W))
  val length_valid = RegInit(false.B)
  val flag_aa = RegInit(false.B)
  val flag_aa_valid = RegInit(false.B)
  val flag_crc = RegInit(false.B)
  val flag_crc_valid = RegInit(false.B)
  val done = RegInit(false.B)

  //Preamble
  val preamble0 = "b10101010".U
  val preamble1 = "b01010101".U
  val preamble01 = Mux(reg_aa(0) === 0.U, preamble0, preamble1)

  //Handshake Parameters
  val out_valid = RegInit(false.B)
  val out_fire = io.out.ready & io.out.valid
  val in_ready = RegInit(Bool(), false.B)
  val in_fire = io.in.ready & io.in.valid

  //data registers

  val data = RegInit(VecInit(Seq.fill(8)(false.B)))

  //crc
  val crc_reset = (state === idle)
  val crc_data = Wire(UInt(1.W))
  val crc_valid = Wire(Bool())
  val crc_result = Wire(UInt(24.W))
  val crc_seed = "b010101010101010101010101".U

  //whitening
  val dewhite_reset = (state === idle)
  val dewhite_data = Wire(UInt(1.W))
  val dewhite_valid = Wire(Bool())
  val dewhite_result = Wire(UInt(1.W))
  val dewhite_seed = "b1100101".U



  //output function
  when(state === idle || state === preamble){
    io.out.bits.data := 0.U
  }.otherwise{//aa, pdu_header, pdu_payload, crc
    io.out.bits.data := data.asUInt
  }

  when(state === wait_dma && io.out.ready)
  {
    io.out.bits.done := true.B
  }.otherwise{
    io.out.bits.done := false.B
  }

  io.out.bits.length := length
  io.out.bits.length_valid := length_valid
  io.out.bits.flag_aa := flag_aa
  io.out.bits.flag_aa_valid := flag_aa_valid
  io.out.bits.flag_crc := flag_crc
  io.out.bits.flag_crc_valid := flag_crc_valid

  io.out.valid := out_valid
  io.in.ready := in_ready


  when(state === idle){
    when(io.in.bits.switch === true.B && io.in.valid){//note: switch usage
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
    when(counter === 3.U && out_fire === true.B){//note
      state := pdu_header
      counter := 0.U
      counter_byte := 0.U
    }.otherwise{
      state := aa
      when(out_fire === true.B){
        counter := counter+1.U
      }
      when(in_fire === true.B){
        when(counter_byte === 7.U){
          counter_byte := 0.U
        }.otherwise{
          counter_byte := counter_byte+1.U
        }
      }
    }
  }.elsewhen(state === pdu_header){
    when(counter === 1.U && out_fire === true.B){//note
      state := pdu_payload
      counter := 0.U
      counter_byte := 0.U
    }.otherwise{
      state := pdu_header
      when(out_fire === true.B){
        counter := counter+1.U
      }
      when(in_fire === true.B){
        when(counter_byte === 7.U){
          counter_byte := 0.U
        }.otherwise{
          counter_byte := counter_byte+1.U
        }
      }
    }
  }.elsewhen(state === pdu_payload){
    when(counter === length-1.U && out_fire === true.B){//note
      state := crc
      counter := 0.U
      counter_byte := 0.U
    }.otherwise{
      state := pdu_payload
      when(out_fire === true.B){
        counter := counter+1.U
      }
      when(in_fire === true.B){
        when(counter_byte === 7.U){
          counter_byte := 0.U
        }.otherwise{
          counter_byte := counter_byte+1.U
        }
      }
    }
  }.elsewhen(state === crc){
    when(counter === 2.U && out_fire === true.B){//note
      state := wait_dma
      counter := 0.U
      counter_byte := 0.U
    }.otherwise{
      state := crc
      when(out_fire === true.B){
        counter := counter+1.U
      }
      when(in_fire === true.B){
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
  when(state === pdu_header && counter === 1.U && out_fire === true.B){
    length := data.asUInt
    length_valid := true.B
  }.elsewhen(state === idle) {
    length_valid := false.B
  }.otherwise{
    //do nothing: registers preserve value//note
  }

    //Flag_aa
  when(state === aa && counter === 0.U && out_fire === true.B){//note: same as above
    when(data.asUInt =/= reg_aa(7,0)){
      flag_aa := true.B
      flag_aa_valid := true.B
    }
  }.elsewhen(state === aa && counter === 1.U && out_fire === true.B){
    when(data.asUInt =/= reg_aa(15,8)){
      flag_aa := true.B
      flag_aa_valid := true.B
    }
  }.elsewhen(state === aa && counter === 2.U && out_fire === true.B){
    when(data.asUInt =/= reg_aa(23,16)){
      flag_aa := true.B
      flag_aa_valid := true.B
    }
  }.elsewhen(state === aa && counter === 3.U && out_fire === true.B){
    when(data.asUInt =/= reg_aa(31,24)){
      flag_aa := true.B
      flag_aa_valid := true.B
    }.otherwise{
      flag_aa_valid := true.B
    }
  }.otherwise{
    //do nothing: registers preserve value//note
  }

    //Flag_crc
  when(state === crc && counter === 0.U && out_fire === true.B){//note: same as above
    when(data.asUInt =/= crc_result(7,0)){
      flag_crc := true.B
      flag_crc_valid := true.B
    }
  }.elsewhen(state === crc && counter === 1.U && out_fire === true.B){
    when(data.asUInt =/= crc_result(15,8)){
      flag_crc := true.B
      flag_crc_valid := true.B
    }
  }.elsewhen(state === crc && counter === 2.U && out_fire === true.B){
    when(data.asUInt =/= crc_result(23,16)){
      flag_crc := true.B
      flag_crc_valid := true.B
    }.otherwise{
      flag_crc_valid := true.B
    }
  }.otherwise{
    //do nothing: registers preserve value//note
  }



  //out_valid
  when(state === idle || state === preamble){
    out_valid := false.B
  }.otherwise{//aa, pdu_header, pdu_payload, crc
    when(counter_byte === 7.U && in_fire === true.B){
      out_valid := true.B
    }.elsewhen(out_fire === true.B){
      out_valid := false.B
    }
  }

  //AFIFO_Ready_w//note:check corner cases
  when(state === idle){
    in_ready := false.B
  }.elsewhen(state === preamble){
    in_ready := true.B
  }.otherwise{//aa, pdu_header, pdu_payload, crc
    when(counter_byte === 7.U && in_fire === true.B){
      in_ready := false.B
    }.elsewhen(out_fire === true.B){
      in_ready := true.B
    }
  }

  //data
  when(state === pdu_header || state === pdu_payload || state === crc){
    when(in_fire === true.B){
      //data(counter_byte) := dewhite_result.toBools
      when(dewhite_result===0.U){
        data(counter_byte) := false.B
      }.otherwise{
        data(counter_byte) := true.B
      }
    }
  }.elsewhen(state === preamble){
    when(in_fire === true.B){
      //data(7) := io.in.bits.data.toBools //note: subword assignment
      when(io.in.bits.data === 0.U){
        data(7) := false.B
      }.otherwise{
        data(7) := true.B
      }
      for(i <- 0 to 6){//value shifting
        data(i) := data(i+1)
      }
    }
  }.elsewhen(state === aa){
    when(in_fire === true.B){
      //data(counter_byte) := io.in.bits.data.toBools
      when(io.in.bits.data === 0.U){
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
    crc_valid := in_fire
  }.otherwise{
    crc_data := 0.U
    crc_valid := false.B
  }


  //dewhitening
  when(state === pdu_header || state === pdu_payload || state === crc){//check corner cases
    dewhite_data  := io.in.bits.data
    dewhite_valid := in_fire
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
  val WHITE_inst = Module(new Whitening)

  WHITE_inst.io.init := dewhite_reset
  WHITE_inst.io.operand.bits := dewhite_data
  WHITE_inst.io.operand.valid := dewhite_valid
  dewhite_result := WHITE_inst.io.result.bits
  WHITE_inst.io.result.ready := true.B
  WHITE_inst.io.seed := dewhite_seed

}
