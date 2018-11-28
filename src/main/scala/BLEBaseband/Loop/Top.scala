package PacketDisAssembler
package PacketAssembler

import PacketDisAssembler._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util.DontTouch

class ExampleTop(implicit p: Parameters) extends RocketSubsystem
    with CanHaveMasterAXI4MemPort
    with HasPeripheryBootROM
    with HasSyncExtInterrupts {
  override lazy val module = new ExampleTopModule(this)
}

class ExampleTopModule[+L <: ExampleTop](l: L) extends RocketSubsystemModuleImp(l)
    with HasRTCModuleImp
    with CanHaveMasterAXI4MemPortModuleImp
    with HasPeripheryBootROMModuleImp
    with HasExtInterruptsModuleImp
    with DontTouch

class ExampleTopWithPDA(implicit p: Parameters) extends ExampleTop
    with HasPeripheryPDA {
  override lazy val module = new ExampleTopModule(this)
}

class ExampleTopWithPA(implicit p: Parameters) extends ExampleTop
    with HasPeripheryPA {
  override lazy val module = new ExampleTopModule(this)
}
