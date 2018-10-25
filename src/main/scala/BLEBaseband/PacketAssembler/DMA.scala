package dma194

import chisel3._
import chisel3.util._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._
import freechips.rocketchip.config.Parameters

class DMA(implicit p: Parameters) extends LazyModule
{
  val node = TLClientNode(Seq(TLClientPortParameters(Seq(TLClientParameters(name = "dma", sourceId = IdRange(0,1))))))
  lazy val module = new DMAImp(this, 32)
}

class DMAImp(outer: DMA, width: Int) extends LazyModuleImp(outer) {
  val io = IO(new Bundle {
    // TileLink2 interface
    //val pa = Flipped(new PacketAssemblerBundle(width))
    //val pd = new PacketAssemblerBundle(width)
    val rxStartAddress = Input(UInt(32.W))
    val rxEndAddress = Input(UInt(32.W))
    val txStartAddress = Input(UInt(32.W))
    val txEndAddress = Input(UInt(32.W))
    val txfinished = Output(Bool())
    val rxfinished = Output(Bool())
    val rxIn = Flipped(Irrevocable(UInt(32.W)))
    val txOut = Irrevocable(UInt(8.W))
    val txStart = Input(Bool())
    val rxStart = Input(Bool())
    val length = Irrevocable(UInt(9.W))
    val interrupt = Output(Bool())
  })

  val mto = Module(new MultipleToOne(4))
  mto.io.out <> io.txOut
  val sIdle :: sActive :: Nil = Enum(2) // Will add more states based on packet interface
  val rxCurrAddress = RegInit(0.U(32.W))
  val txCurrAddress = RegInit(0.U(32.W))
  val rxstate = RegInit(sIdle)
  val txstate = RegInit(sIdle)

  val (tl_out, edgeOut) = outer.node.out(0)
  val a = tl_out.a
  val d = tl_out.d
  tl_out.c.valid := false.B
  tl_out.e.valid := false.B
  tl_out.b.ready := true.B
  val txWorking = RegInit(false.B)

  when (io.txStart && !txWorking) {
    txWorking := true.B
  }

  def rxDone() : Bool = {
    // Check rx done conditions
    (rxCurrAddress === io.rxEndAddress) | (rxstate === sIdle)
  }


  val sNeither :: sTx :: sRx :: Nil = Enum(3) // Will add more states based on packet interface
  val tlOwner = RegInit(sNeither)
  val preference = RegInit(sRx)
  val flight = RegInit(false.B)
  val lengthValid = RegInit(false.B)
  val length = RegInit(0.U(9.W))
  val count = RegInit(0.U(9.W))
  val txData = RegInit(0.U(32.W))
  val txWaitOut = RegInit(false.B)
  val txWaitLength = RegInit(false.B)
  
  when (tlOwner === sNeither) {
    when(preference === sRx && !rxDone() && io.rxIn.valid) {
      tlOwner := sRx
      preference := sTx
    }
    .elsewhen(preference === sTx && !txDone() && txWorking && !txWaitOut && !txWaitLength) {
      tlOwner := sTx
      preference := sRx
    }.otherwise {
      when(!rxDone() && io.rxIn.valid) {
        tlOwner := sRx
        preference := sTx
      }
      .elsewhen(!txDone() && txWorking && !txWaitOut && !txWaitLength) {
        tlOwner := sTx
        preference := sRx
      }
    }
  }

  d.ready := flight
  //a.valid := !flight && (state === sActive) && (!rxDone()) && io.rxIn.valid
  a.valid := false.B
  a.bits := edgeOut.Put(0.U, rxCurrAddress, 2.U, io.rxIn.bits)._2
  io.rxIn.ready := false.B
  // printf(p"flight ${flight} fromTL ${a.fire()}\n")
  // printf(p"$tlOwner \n")
  def doRx() : Unit = {
    // Read from packet disassembler and put into RX FIFO
    // Put data from FIFO into memory
    // Increment counter
    when(flight === false.B && (tlOwner === sRx)) {
      a.bits := edgeOut.Put(0.U, rxCurrAddress, 2.U, io.rxIn.bits)._2
      // start a new request
      a.valid := true.B
      when(a.fire()) {
        // printf(p"bits: ${io.rxIn.bits}\n")
        flight := true.B
        io.rxIn.ready := true.B
      }
    }.elsewhen(tlOwner === sRx) {
      when(d.fire()) {
        rxCurrAddress := rxCurrAddress + 4.U
        flight := false.B
        tlOwner := sNeither
      }
    }
  }

  def txDone() : Bool = {
    // Check tx done conditions
    ((txCurrAddress === io.txEndAddress) && !txWaitLength && !txWaitOut) | (txstate === sIdle)
  }

  mto.io.in.bits := txData
  mto.io.in.valid := txWaitOut
  io.length.bits := length
  io.length.valid := txWaitLength
  when (txWaitLength && io.length.ready) {
    txWaitLength := false.B
  }

  when (count <= length) {
    mto.io.num := 4.U
  }.otherwise {
    mto.io.num := length - (count - 4.U)
  }

  io.interrupt := false.B
  def doTx() : Unit = {
    // Read from Tx FIFO into packet assembler
    // Put data from FIFO into memory
    // Increment counter
    when(flight === false.B && (tlOwner === sTx)) {
      a.bits := edgeOut.Get(0.U, txCurrAddress, 2.U)._2
      // start a new request
      a.valid := true.B
      when(a.fire()) {
        flight := true.B
      }
    }.elsewhen(tlOwner === sTx) {
      when(d.fire()) {
        when (!lengthValid) {
          length := edgeOut.data(d.bits)
          count := 0.U
          lengthValid := true.B
        }.otherwise {
          txData := edgeOut.data(d.bits)
          txWaitOut := true.B
          count := count + 4.U
          when (count + 4.U >= length) {
            txWaitLength := true.B
            txWorking := false.B
            io.interrupt := true.B
            lengthValid := false.B
          }
        }
        txCurrAddress := txCurrAddress + 4.U
        flight := false.B
        tlOwner := sNeither
      }
    }
    when(txWaitOut) {
      when(mto.io.in.ready) {
        txWaitOut := false.B
        when (count >= length) {
            count := 0.U
        }
      }
    }
  }

  io.txfinished := (txstate === sIdle)
  switch(txstate) {
    is(sIdle) {
      // Empty FIFOs, stop memory requests, reset everything to 0
      when(io.txStart) {
        lengthValid := false.B
        length := false.B
        count := 0.U
        txData := 0.U
        txWaitOut := false.B
        txWaitLength := false.B
        txstate := sActive
        txCurrAddress := io.txStartAddress
      }
    }
    is(sActive) {
      when(!txDone()){
        doTx()
      }.otherwise {
        txstate := sIdle
      }
    }
  }
  io.rxfinished := (txstate === sIdle)
  switch(rxstate) {
    is(sIdle) {
      // Empty FIFOs, stop memory requests, reset everything to 0
      when(io.rxStart) {
        rxstate := sActive
        rxCurrAddress := io.rxStartAddress
      }
    }
    is(sActive) {
      when(!rxDone()) {
        doRx()
      }.otherwise {
        rxstate := sIdle
      }
    }
  }

}
