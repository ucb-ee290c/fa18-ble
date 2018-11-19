package CRC // belongs to folder

import chisel3._
import chisel3.util._



// CRC module for data[0:0] ,   crc[23:0]=1+x^1+x^3+x^4+x^6+x^9+x^10+x^24;
//-----------------------------------------------------------------------------
/*
module crc(
  input [0:0] data_in,
  input crc_en,
  output [23:0] crc_out,
  input rst,
  input clk);

  reg [23:0] lfsr_q,lfsr_c;

  assign crc_out = lfsr_q;

  always @(*) begin
    lfsr_c[0] = lfsr_q[23] ^ data_in[0];
    lfsr_c[1] = lfsr_q[0] ^ lfsr_q[23] ^ data_in[0];
    lfsr_c[2] = lfsr_q[1];
    lfsr_c[3] = lfsr_q[2] ^ lfsr_q[23] ^ data_in[0];
    lfsr_c[4] = lfsr_q[3] ^ lfsr_q[23] ^ data_in[0];
    lfsr_c[5] = lfsr_q[4];
    lfsr_c[6] = lfsr_q[5] ^ lfsr_q[23] ^ data_in[0];
    lfsr_c[7] = lfsr_q[6];
    lfsr_c[8] = lfsr_q[7];
    lfsr_c[9] = lfsr_q[8] ^ lfsr_q[23] ^ data_in[0];
    lfsr_c[10] = lfsr_q[9] ^ lfsr_q[23] ^ data_in[0];
    lfsr_c[11] = lfsr_q[10];
    lfsr_c[12] = lfsr_q[11];
    lfsr_c[13] = lfsr_q[12];
    lfsr_c[14] = lfsr_q[13];
    lfsr_c[15] = lfsr_q[14];
    lfsr_c[16] = lfsr_q[15];
    lfsr_c[17] = lfsr_q[16];
    lfsr_c[18] = lfsr_q[17];
    lfsr_c[19] = lfsr_q[18];
    lfsr_c[20] = lfsr_q[19];
    lfsr_c[21] = lfsr_q[20];
    lfsr_c[22] = lfsr_q[21];
    lfsr_c[23] = lfsr_q[22];

  end // always

  always @(posedge clk, posedge rst) begin
    if(rst) begin
      lfsr_q <= {24{1'b1}};
    end
    else begin
      lfsr_q <= crc_en ? lfsr_c : lfsr_q;
    end
  end // always
endmodule // crc
*/

class Serial_CRC extends Module {
    val io      = IO(new Bundle {
    val operand  = Flipped(Decoupled(UInt(1.W)))
    val result   = Decoupled(UInt(24.W))

   	val seed       = Input(UInt(24.W))
    val init       = Input(Bool())        // to init the seed

  // val crc_out = Output(UInt(24.W))
  })

    val lfsr = Reg(init = 0.U(24.W)) // x^24 + x^10 + x^9 + x^6 + x^4 + x^3 + x^1 + 1
    val inv  = lfsr(0) ^ io.operand.bits

    // Position 0 shall be set as the least significant bit and 
    // position 23 shall be set as the most significant bit of the initialization value. 
    // The CRC is transmitted most significant bit first, 
    // i.e. from position 23 to position 0 (see Section 1.2).


    //val output_bit_reg = Reg(init = 0.U(1.W))
    val output_valid = Reg(Bool())

    when (io.init === true.B) { 
        lfsr := Reverse(io.seed)
        //output_bit_reg := 0.U
        output_valid := true.B
        io.operand.ready := true.B

    } .elsewhen (io.operand.valid === true.B) {
	    // LFSR follows the Figure 3.3 of BLE spec version 5.0 vol 6
      // position 0 (left most) is represented by lfsr[23]
      // position 23 (right most) is represented by lfsr[0]
      lfsr := Cat(inv, lfsr(23)^inv, lfsr(22),lfsr(21)^inv,lfsr(20)^inv,lfsr(19),lfsr(18)^inv, lfsr(17), 
        lfsr(16),lfsr(15)^inv,lfsr(14)^inv,lfsr(13),lfsr(12),lfsr(11),lfsr(10),lfsr(9),lfsr(8), 
        lfsr(7), lfsr(6),lfsr(5),lfsr(4),lfsr(3),lfsr(2),lfsr(1))

    	//output_bit_reg := inv
     	output_valid := true.B
     	io.operand.ready := true.B
        
    } .otherwise {
        lfsr := lfsr
        output_valid := true.B
        io.operand.ready := true.B
    }

	io.result.valid := output_valid
  //io.crc_out := lfsr
 	io.result.bits  := lfsr
    
}