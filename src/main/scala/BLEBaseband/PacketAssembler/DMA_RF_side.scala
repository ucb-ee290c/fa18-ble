package dma194

import chisel3._
import chisel3.util._
import chisel3.experimental.withClock

// DMA RF controller side implementation
class DMA_RF extends Module {
  val io = IO(new Bundle {

  	/************* Tx interface ***************/
    val tl_tx_trigger = Input(Bool())
    val pa_tx_trigger = Output(Bool())

    val tl_tx_data = Flipped(Decoupled(UInt(8.W)))
    val pa_tx_data = Decoupled(UInt(8.W))

    val tl_tx_length = Flipped(Irrevocable(UInt(9.W)))
    val pa_tx_length = Irrevocable(UInt(8.W))

    val pa_tx_done = Input(Bool())
    val tl_tx_done = Output(Bool())

    /************* Rx interface ***************/
    val pa_rx_trigger = Input(Bool())
    val tl_rx_trigger = Output(Bool())

    val pa_rx_data = Flipped(Decoupled(UInt(8.W)))
    val tl_rx_data = Decoupled(UInt(8.W))

    val pa_rx_length = Flipped(Irrevocable(UInt(8.W)))
    val tl_rx_length = Irrevocable(UInt(9.W))

    val pa_rx_FlagAA = Flipped(Decoupled(Bool()))
    val tl_rx_FlagAA = Decoupled(Bool())

    val pa_rx_FlagCRC = Flipped(Decoupled(Bool()))
    val tl_rx_FlagCRC = Decoupled(Bool())

    val pa_rx_done = Output(Bool())
    val tl_rx_done = Input(Bool())

  })

// data FIFO
val txfifo = Module(new Queue(UInt(8.W), 64))


val rxfifo = Module(new Queue(UInt(8.W), 32))


io.pa_tx_length <> io.tl_tx_length

io.pa_rx_length <> io.tl_rx_length
io.tl_rx_length.bits := io.pa_rx_length.bits + 4.U + 2.U + 3.U // Access Address and PDU Header

io.pa_rx_FlagCRC <> io.tl_rx_FlagCRC
io.pa_rx_FlagAA <> io.tl_rx_FlagAA

io.pa_tx_trigger := io.tl_tx_trigger
io.tl_rx_trigger := io.pa_rx_trigger

// FSM
val s_Idle :: s_Txdata :: s_Rxdata :: Nil = Enum(3)
val state = Reg(init = s_Idle)

	switch(state)
	{
		is (s_Idle)
		{
			when (io.tl_tx_trigger)
			{
				state := s_Txdata
			}
			.elsewhen (io.pa_rx_trigger)
			{
				state := s_Rxdata
			}
		}

		is (s_Txdata) // start reading data from TL to fifo and then to PA
		{
			txfifo.io.enq <> io.tl_tx_data	// TL to fifo
			txfifo.io.deq <> io.pa_tx_data	// fifo to PA
			when (io.pa_tx_done)
			{
				io.tl_tx_done := true.B
				state := s_Idle
			}
		}

		is (s_Rxdata) // start writing data from PDA to fifo and then to TL
		{
			rxfifo.io.enq <> io.pa_rx_data	// PDA to fifo
			rxfifo.io.deq <> io.tl_rx_data	// fifo to TL
			when (io.pa_rx_done)
			{
				io.pa_rx_done := true.B
				state := s_Idle
			}
		}

	}

}
