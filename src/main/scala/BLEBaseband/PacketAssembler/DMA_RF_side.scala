package dma194

import chisel3._
import chisel3.util._
import chisel3.experimental.withClock

// Chen Fu: DMA RF controller side implementaion
class DMA_RF extends Module {
  val io = IO(new Bundle {

  	/************* Tx interface ***************/
    val TL_Tx_Trigger = Input(Bool())
    val PA_Tx_Trigger = Output(Bool())

    val TL_Tx_fifo = Flipped(Decoupled(UInt(8.W)))
    val PA_Tx_fifo = Decoupled(UInt(8.W))

    val TL_Tx_length = Flipped(Irrevocable(UInt(9.W)))
    val PA_Tx_length = Irrevocable(UInt(8.W))

    val PA_Tx_done = Input(Bool())
    val TL_Tx_done = Output(Bool())

    /************* Rx interface ***************/
    val PA_Rx_Trigger = Input(Bool())
    val TL_Rx_Trigger = Output(Bool())

    val PA_Rx_fifo = Flipped(Decoupled(UInt(8.W)))
    val TL_Rx_fifo = Decoupled(UInt(8.W))

    val PA_Rx_length = Flipped(Irrevocable(UInt(8.W)))
    val TL_Rx_length = Irrevocable(UInt(9.W))

    val PA_Rx_FlagAA = Flipped(Decoupled(Bool()))
    val TL_Rx_FlagAA = Decoupled(Bool())

    val PA_Rx_FlagCRC = Flipped(Decoupled(Bool()))
    val TL_Rx_FlagCRC = Decoupled(Bool())

    val PA_Rx_done = Input(Bool())

  })

// data FIFO
val Txfifo = Module(new Queue(UInt(8.W), 64))
Txfifo.io.enq <> io.TL_Tx_fifo
Txfifo.io.deq <> io.PA_Tx_fifo

val Rxfifo = Module(new Queue(UInt(8.W), 32))
Rxfifo.io.enq <> io.PA_Rx_fifo
Rxfifo.io.deq <> io.TL_Rx_fifo

io.PA_Tx_length <> io.TL_Tx_length

io.PA_Rx_length <> io.TL_Rx_length
io.TL_Rx_length.bits := io.PA_Rx_length.bits + 4.U + 2.U + 3.U // Access Address and PDU Header

io.PA_Rx_FlagCRC <> io.TL_Rx_FlagCRC

io.PA_Rx_FlagAA <> io.TL_Rx_FlagAA

io.PA_Tx_Trigger := io.TL_Tx_Trigger
io.TL_Rx_Trigger := io.PA_Rx_Trigger

// FSM
val s_Idle :: s_Txdata :: s_Txdone :: s_Rxdata :: Nil = Enum(4)
val state = Reg(init = s_Idle)

	switch(state)
	{
		is (s_Idle)
		{
			when (io.TL_Tx_Trigger)
			{
				state := s_Txdata
			}
			.elsewhen (io.PA_Rx_Trigger)
			{
				state := s_Rxdata
			}
		}
		is (s_Txdata) // start reading data from FIFO and send to RFController
		{
			when (io.PA_Tx_done)
			{
				state := s_Txdone
			}
		}
		is (s_Txdone) // start reading data from FIFO and send to RFController
		{
			state := s_Idle
		}
		is (s_Rxdata) // start writing data to FIFO from RFController
		{
			when (io.PA_Rx_done)
			{
				state := s_Idle
			}
		}
	}
	io.TL_Tx_done := (state === s_Txdone)
}
