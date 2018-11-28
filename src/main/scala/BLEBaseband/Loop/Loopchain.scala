package PacketAssembler
package PacketDisAssembler

import chisel3._
import chisel3.util._
import dspblocks._
import dsptools.numbers._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._


/**
  * The memory interface writes entries into the queue.
  * They stream out the streaming interface.
  */
abstract class WriteQueue
(
  val depth: Int = 8,
  val streamParameters: AXI4StreamMasterParameters = AXI4StreamMasterParameters()
)(implicit p: Parameters) extends LazyModule with HasCSR {
  // stream node, output only
  val streamNode = AXI4StreamMasterNode(streamParameters)

  lazy val module = new LazyModuleImp(this) {
    require(streamNode.out.length == 1)

    // get the output bundle associated with the AXI4Stream node
    val out = streamNode.out(0)._1
    // width (in bits) of the output interface
    val width = 64
    // instantiate a queue
    val queue = Module(new Queue(UInt(width.W), depth))
    // connect queue output to streaming output
    out.valid := queue.io.deq.valid
    out.bits.data := queue.io.deq.bits
    // don't use last
    out.bits.last := false.B
    queue.io.deq.ready := out.ready

    regmap(
      // each write adds an entry to the queue
      0x0 -> Seq(RegField.w(width, queue.io.enq)),
      // read the number of entries in the queue
      (width+7)/8 -> Seq(RegField.r(width, queue.io.count)),
    )
  }
}


class TLWriteQueue
(
  depth: Int = 8,
  csrAddress: AddressSet = AddressSet(0x2000, 0xff),
  beatBytes: Int = 8,
)(implicit p: Parameters) extends WriteQueue(depth) with TLHasCSR {
  val devname = "tlQueueIn"
  val devcompat = Seq("ucb-art", "dsptools")
  val device = new SimpleDevice(devname, devcompat) {
    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(name, mapping)
    }
  }
  // make diplomatic TL node for regmap
  override val mem = Some(TLRegisterNode(address = Seq(csrAddress), device = device, beatBytes = beatBytes))
}


/**
  * The streaming interface adds elements into the queue.
  * The memory interface can read elements out of the queue.
  */
abstract class ReadQueue
(
  val depth: Int = 8,
  val streamParameters: AXI4StreamSlaveParameters = AXI4StreamSlaveParameters()
)(implicit p: Parameters)extends LazyModule with HasCSR {
  val streamNode = AXI4StreamSlaveNode(streamParameters)

  lazy val module = new LazyModuleImp(this) {
    require(streamNode.in.length == 1)


    // get the input bundle associated with the AXI4Stream node
    val in = streamNode.in(0)._1
    // width (in bits) of the input interface
    val width = 64
    // instantiate a queue
    val queue = Module(new Queue(UInt(width.W), depth))
    // connect queue output to streaming output


    queue.io.enq.valid := in.valid
    queue.io.enq.bits := in.bits.data
    in.ready := queue.io.enq.ready
    // don't use last
    //in.bits.last := false.B


    regmap(
      // each read cuts an entry from the queue
      0x0 -> Seq(RegField.r(width, queue.io.deq)),
      // read the number of entries in the queue
      (width+7)/8 -> Seq(RegField.r(width, queue.io.count)),
    )
  }
}


class TLReadQueue
(
  depth: Int = 8,
  csrAddress: AddressSet = AddressSet(0x2100, 0xff),
  beatBytes: Int = 8
)(implicit p: Parameters) extends ReadQueue(depth) with TLHasCSR {
  val devname = "tlQueueOut"
  val devcompat = Seq("ucb-art", "dsptools")
  val device = new SimpleDevice(devname, devcompat) {
    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(name, mapping)
    }
  }
  // make diplomatic TL node for regmap
  override val mem = Some(TLRegisterNode(address = Seq(csrAddress), device = device, beatBytes = beatBytes))

}

abstract class TransitionQueue
(
  val depth: Int = 8,
  val streamParameters: AXI4StreamMasterParameters = AXI4StreamMasterParameters()
)(implicit p: Parameters) extends LazyModule with HasCSR {
  // stream node, output only
  val streamNode = AXI4StreamMasterNode(streamParameters)

  lazy val module = new LazyModuleImp(this) {
    require(streamNode.out.length == 1 && streamNode.in.length == 1)

    // get the output bundle associated with the AXI4Stream node
    val in = streamNode.in(0)._1
    val out = streamNode.out(0)._1
    // width (in bits) of the output interface
    val width = 64
    // instantiate a queue
    val queue = Module(new Queue(UInt(width.W), depth))
    // connect queue output to streaming output
    out.valid := queue.io.deq.valid
    out.bits.data := queue.io.deq.bits
    // don't use last
    out.bits.last := false.B
    queue.io.deq.ready := out.ready
    queue.io.enq.valid := in.valid
    queue.io.enq.bits := in.bits.data
    in.ready := queue.io.enq.ready

    /*regmap(
      // each write adds an entry to the queue
      0x0 -> Seq(RegField.w(width, queue.io.enq)),
      // read the number of entries in the queue
      (width+7)/8 -> Seq(RegField.r(width, queue.io.count)),
    )
    regmap(
      // each read cuts an entry from the queue
      0x0 -> Seq(RegField.r(width, queue.io.deq)),
      // read the number of entries in the queue
      (width+7)/8 -> Seq(RegField.r(width, queue.io.count)),
    )*/
  }
}


class TLTransitionQueue
(
  depth: Int = 8,
  csrAddress: AddressSet = AddressSet(0x2200, 0xff),
  beatBytes: Int = 8,
)(implicit p: Parameters) extends TransitionQueue(depth) with TLHasCSR {
  val devname = "tlQueueTransition"
  val devcompat = Seq("ucb-art", "dsptools")
  val device = new SimpleDevice(devname, devcompat) {
    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(name, mapping)
    }
  }
  // make diplomatic TL node for regmap
  override val mem = Some(TLRegisterNode(address = Seq(csrAddress), device = device, beatBytes = beatBytes))
}


abstract class PABlock[D, U, EO, EI, B <: Data] (implicit p: Parameters) extends DspBlock[D, U, EO, EI, B] {
  val streamNode = AXI4StreamIdentityNode()
  val mem = None

  lazy val module = new LazyModuleImp(this) {
    require(streamNode.in.length == 1)
    require(streamNode.out.length == 1)

    val in = streamNode.in.head._1
    val out = streamNode.out.head._1

    //unpack and pack
    val packet = Module(new PacketAssembler())
    packet.io.in.bits := in.bits.data.asTypeOf(new PAInputBundle())
    packet.io.in.valid := in.valid
    in.ready := packet.io.in.ready

    out.valid := packet.io.out.valid
    packet.io.out.ready := out.ready

    out.bits.data := packet.io.out.bits.asUInt()
  }
}

class TLPABlock(implicit p: Parameters)extends
  PABlock[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle] with TLDspBlock



abstract class PDABlock[D, U, EO, EI, B <: Data] (implicit p: Parameters) extends DspBlock[D, U, EO, EI, B] {
  val streamNode = AXI4StreamIdentityNode()
  val mem = None

  lazy val module = new LazyModuleImp(this) {
    require(streamNode.in.length == 1)
    require(streamNode.out.length == 1)

    val in = streamNode.in.head._1
    val out = streamNode.out.head._1

    //unpack and pack
    val packet = Module(new PacketDisAssembler())
    packet.io.in.bits := in.bits.data.asTypeOf(new PDAInputBundle())
    packet.io.in.valid := in.valid
    in.ready := packet.io.in.ready

    out.valid := packet.io.out.valid
    packet.io.out.ready := out.ready

    out.bits.data := packet.io.out.bits.data.asUInt()
  }
}

class TLPDABlock(implicit p: Parameters)extends
  PDABlock[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle] with TLDspBlock

class LoopThing
(
  val depthWrite: Int = 32,
  val depthRead : Int = 256,
  val depthTransition: Int = 256
)(implicit p: Parameters) extends LazyModule {
  // instantiate lazy modules
  val writeQueue = LazyModule(new TLWriteQueue(depthWrite))
  val packet_pa = LazyModule(new TLPABlock())
  val packet_pda = LazyModule(new TLPDABlock())
  val readQueue = LazyModule(new TLReadQueue(depthRead))
  val transitionQueue = LazyModule(new TLTransitionQueue(depthTransition))

  // connect streamNodes of queues and cordic
  readQueue.streamNode := packet_pda.streamNode := transitionQueue.streamNode := packet_pa.streamNode := writeQueue.streamNode//? transitionQueue.streamNode

  lazy val module = new LazyModuleImp(this)
}

