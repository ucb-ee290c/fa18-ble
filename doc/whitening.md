# Whitening
A whitening module is used to prevent a long sequence of zeros or ones. It is similar to a CRC despite some subtle difference.
 
 ## Input and Output Ports
 ```
 class Whitening extends Module {
    val io = IO(new Bundle {
        val operand  = Flipped(DecoupledIO(UInt(1.W)))
        val result   = Decoupled(UInt(1.W))
        val seed     = Input(UInt(7.W))
        val init     = Input(Bool())       
  })
}
 ```
 Like a CRC module, a whitening has three inputs and one output. `init` is a 1-bit input sent by the packet assembler as the signal of the beginning of the sequence. `seed` is a 24-bit input that CRC module needs to load to the corresponding registers. `operand` is a 1-bit input for bits to perform certain operations. `result` is a 24-bit output produced by whitening and taken by the packet assembler. 
