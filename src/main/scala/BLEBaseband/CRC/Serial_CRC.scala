package CRC // belongs to folder

import chisel3._
import chisel3.util._


class Serial_CRC extends Module {
    val io      = IO(new Bundle {
    val operand  = new DecoupledIO(UInt(1.W)).flip()
    val result   = new DecoupledIO(UInt(24.W))

   	val seed       = Input(UInt(24.W))
    val init       = Input(Bool())        // to init the seed

  })

    val lfsr = Reg(init = 0.U(24.W)) // x^24 + x^10 + x^9 + x^6 + x^4 + x^3 + x^1 + 1
    val inv  = lfsr(0) ^ io.operand.bits

    // Position 0 shall be set as the least significant bit and 
    // position 23 shall be set as the most significant bit of the initialization value. 
    // The CRC is transmitted most significant bit first, 
    // i.e. from position 23 to position 0 (see Section 1.2).

    val output_valid_reg = Reg(Bool())

    when (io.init === true.B) { 
        lfsr := Reverse(io.seed)
        //output_bit_reg := 0.U
        output_valid_reg := true.B
        io.operand.ready := true.B

    } .elsewhen (io.operand.valid === true.B) {
	    // LFSR follows the Figure 3.3 of BLE spec version 5.0 vol 6
      // position 0 (left most) is represented by lfsr[23]
      // position 23 (right most) is represented by lfsr[0]
      lfsr := Cat(inv, lfsr(23)^inv, lfsr(22),lfsr(21)^inv,lfsr(20)^inv,lfsr(19),lfsr(18)^inv, lfsr(17), 
        lfsr(16),lfsr(15)^inv,lfsr(14)^inv,lfsr(13),lfsr(12),lfsr(11),lfsr(10),lfsr(9),lfsr(8), 
        lfsr(7), lfsr(6),lfsr(5),lfsr(4),lfsr(3),lfsr(2),lfsr(1))

     	output_valid_reg := true.B
     	io.operand.ready := true.B
        
    } .otherwise {
        lfsr := lfsr
        output_valid_reg := true.B
        io.operand.ready := true.B
    }

	  io.result.valid := output_valid_reg
 	  io.result.bits  := lfsr
    
}