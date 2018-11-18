package PacketAssembler.test

import PacketAssembler._
import chisel3._
import chisel3.util._
import chisel3.iotesters.{PeekPokeTester, Driver, ChiselFlatSpec}


class PacketAssemblerTest(c: PacketAssembler) extends PeekPokeTester(c) {
//IO reference
    /*val in.trigger = Input(Bool())
    val in.data.bits = Flipped(Decoupled(UInt(8.W)))//decouple(sink): data, pop, empty
    val REG_CRC_Seed_i = Input(UInt(24.W))
    val REG_White_Seed_i = Input(UInt(7.W))

    val in.done_o = Output(Bool())	
	
    //AFIFO
    val out = Decoupled(UInt(1.W))//decouple(source): data, puch, full*/

//scala declaration
	/*
//hard-coded arbitrary test
	val wholepacket = "h11001010100101071001101001".U//AA first bit: 1; length:7; total length: 26(13 bytes)
	//val wholepacket_neg = ~wholepacket
	val preamble = "b01010101".U
	val CRC = "h101001".U
	*/
//hard-coded David's test
//0001110101010101101011011111011001000101110001111100010100001110001001100001001111000010101011001001100000110111101110000011000010100001110010011110010010010011011101011011011101000001011011001101000100101101101110
	val wholepacket_dig = "b01101011011111011001000101110001_0100000000001000_011000110000000101001100010011100100000000000000_01000000100000001010000001100000000100001100101011000010101010101011001011001100".U
	val wholepacket_rad = "b01101011011111011001000101110001_1111000101000011_100010011000010011110000101010110010011000001101_11101110000011000010100001110010011110010010010011011101011011011101000001011011".U
	//---------------------_--------------AA----------------_---PDU header---_-----------PDU advertiser address---------------_-----------------PDU data1 and data2--------------------------------------------
	val random_sequence = "b000111".U//pre-preamble
	val preamble = "b01010101".U
	val CRC_rad = "b001101000100101101101110".U

	val wholepacket_dig_rev = "b00110011010011010101010101000011010100110000100000000110000001010000000100000010000000000000001001110010001100101000000011000110000100000000001010001110100010011011111011010110".U
	val wholepacket_rad_rev = "b11011010000010111011011010111011001001001001111001001110000101000011000001110111101100000110010011010101000011110010000110010001110000101000111110001110100010011011111011010110".U
	val preamble_rev = "b10101010".U
	val CRC_rad_rev = "b011101101101001000101100".U

//reset
	//reset(3)

//throughout packet
	//poke(c.io.in.crc_seed,"b010101010101010101010101".U)
	//poke(c.io.in.white_seed,"b1100101".U)

//initialize
	poke(c.io.in.bits.trigger,false.B)
	poke(c.io.in.valid,false.B)
	poke(c.io.in.bits.data,0.U)
	poke(c.io.out.ready,false.B)

	step(2)

//trigger
	poke(c.io.in.bits.trigger,true.B)
	poke(c.io.in.valid,true.B)
	poke(c.io.in.bits.data, wholepacket_dig_rev(7,0))
	step(1)
	poke(c.io.in.bits.trigger,false.B)

//PREAMBLE
	var j:Int = 0
	for(j<-0 to 7){
		//step(Random_Num(1,100))
		step(5)
		poke(c.io.out.ready,true.B)
   		expect(c.io.out.bits.data, preamble_rev(j))//note: U to B
   		//println(s"${peek(c.io.out.bits.data)}")
   		//println(s"${peek(preamble_rev(j))}")
   		step(1)
 		poke(c.io.out.ready,false.B)//need to test two ready  				
	}
	//step(Random_Num(8,100))
	step(10)
//AA
	for(j<-0 to 31){
		if(j%8==0){
			poke(c.io.in.bits.data,wholepacket_dig_rev((j/8)*8+7,(j/8)*8))
			poke(c.io.in.valid,true.B)
		}else{
			poke(c.io.in.valid,false.B)			
		}
		//println(s"${(j/8)*8}")
		//step(Random_Num(2,100))//minimun for DMA_fire: 2
		step(5)
		poke(c.io.out.ready,true.B)
   		expect(c.io.out.bits.data, wholepacket_rad_rev(j))//note
   		//println(s"j="+j+s"\n${peek(c.io.out.bits.data)}\t${peek(wholepacket_rad_rev(j))}")
   		step(1)
 		poke(c.io.out.ready,false.B)//need to test two ready  				
	}
	//step(Random_Num(8,100))
	step(10)
//PDU_HEADER
	for(j<-32 to 47){
		if(j%8==0){
			poke(c.io.in.bits.data,wholepacket_dig_rev((j/8)*8+7,(j/8)*8))
			poke(c.io.in.valid,true.B)
		}else{
			poke(c.io.in.valid,false.B)			
		}
		
		//step(Random_Num(2,100))//minimun for DMA_fire: 2
		step(5)
		poke(c.io.out.ready,true.B)
   		//println(s"j="+j+s"\n${peek(c.io.out.bits.data)}\t${peek(wholepacket_rad_rev(j))}")		
   		expect(c.io.out.bits.data, wholepacket_rad_rev(j))//note
   		step(1)
 		poke(c.io.out.ready,false.B)//need to test two ready  				
	}
	//step(Random_Num(8,100))
	step(10)
//PDU_PAYLOAD
	for(j<-48 to 22*8-1){
		if(j%8==0){
			poke(c.io.in.bits.data,wholepacket_dig_rev((j/8)*8+7,(j/8)*8))
			poke(c.io.in.valid,true.B)
		}else{
			poke(c.io.in.valid,false.B)			
		}
		//step(Random_Num(2,100))//minimun for DMA_fire: 2
		step(5)
		poke(c.io.out.ready,true.B)
   		expect(c.io.out.bits.data, wholepacket_rad_rev(j))//note

   		step(1)
 		poke(c.io.out.ready,false.B)//need to test two ready  				
	}
	//step(Random_Num(8,100))
	step(10)
//CRC
	for(j<-0 to 21){
		//step(Random_Num(1,100))
		step(5)
		poke(c.io.out.ready,true.B)
   		expect(c.io.out.bits.data, CRC_rad_rev(j))//note
   		step(1)
 		poke(c.io.out.ready,false.B)//need to test two ready consequently		
	}
	j=22
	//poke(c.io.in.bits.data,CRC((j/8)*8+7,(j/8)*8))
	step(1)
	poke(c.io.out.ready,true.B)
   	expect(c.io.out.bits.data, CRC_rad_rev(j))//note

	j=23
	//poke(c.io.in.bits.data,CRC((j/8)*8+7,(j/8)*8))
	step(1)
	poke(c.io.out.ready,true.B)
   	expect(c.io.out.bits.data, CRC_rad_rev(j))//note

	expect(c.io.out.bits.done, true.B)//note	



//todo: add FIFO
//todo: add invalid DMA
//todo: check output: DMA_ready

}

class PacketAssemblerTester extends ChiselFlatSpec {
	behavior of "PacketAssembler"
	backends foreach {backend =>
		it should s"perform correct operation as an packet assembler $backend" in { 
			Driver(() => new PacketAssembler, "verilator") { 
				(c) => new PacketAssemblerTest(c)} should be (true)
			}
		}
}
/*
01010101 01101011 01111101 10010001 01110001 //AA and preamble

11110001 00000000 
11110001 01000011 //PDU header
   02		10

11010010 01001010 10100110 11001011 11111100 11100101 //PDU AA, ours
10001001 10000100 11110000 10101011 00100110 00001101 //PDU AA, correct

00100110 00000000 00000000
11101110 00001100 00101000 01110010 01111001 00100100 11011101 01101101 11010000 01011011 //PDU payload, correct
  02		01		05		  06	   08		53		 43		  55	   4D		33

00011101 11000011 10110110 //CRC, ours
00110100 01001011 01101110 //CRC, theirs



01010101 01101011 01111101 10010001 01110001

11110001 01000011 

10001001 10000100 11110000 10101011 00100110 00001101 

11101110 00001100 00101000 01110010 01111001 00100100 11011101 01101101 11010000 01011011 pdu payload

00110100 11001011 01101110

0101010101101011011111011001000101110001111100010100001110001001100001001111000010101011001001100000110111101110000011000010100001110010011110010010010011011101011011011101000001011011001101000100101101101110
010101010110101101111101100100010111000111110001010000111000100110000100111100001010101100100110000011011110111000001100001010000111001001111001001001001101110101101101110100000101101100110100
010101010110101101111101100100010111000111110001010000111000100110000100111100001010101100100110000011011110111000001100001010000111001001111001001001001101110101101101110100000101101100110100110010110110111
*/