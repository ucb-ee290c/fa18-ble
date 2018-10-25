package Whitening // belongs to folder

import chisel3._
import chisel3.util._


class Whitening extends Module {
    val io = IO(new Bundle {
        val operand  = Flipped(DecoupledIO(UInt(1.W)))
        val result   = Decoupled(UInt(1.W))
        val seed     = Input(UInt(7.W))
        val init     = Input(Bool())        // to init the seed
  })

    val whitening_lfsr = Reg(init = 0.U(7.W)) // 1+x^4+x^7;
    val inv           = whitening_lfsr(0)

    val output_bit = Wire(UInt(1.W))
    val output_valid = Wire(Bool())

    output_bit := 0.U
    output_valid := false.B
    io.operand.ready := true.B              // This module shouldn't need this signal
    when (io.init === true.B) { 
        whitening_lfsr := io.seed
        output_bit := 0.U
        output_valid := false.B

    } .elsewhen (io.operand.valid === true.B) {
        whitening_lfsr := Cat(inv, whitening_lfsr(6), whitening_lfsr(5), whitening_lfsr(4), whitening_lfsr(3)^inv, whitening_lfsr(2), whitening_lfsr(1))
        output_bit := inv ^ io.operand.bits
        output_valid := true.B
        
    } .otherwise {
        whitening_lfsr := whitening_lfsr
        output_valid := false.B
    }
    
    io.result.valid := output_valid
    io.result.bits := output_bit
    
}