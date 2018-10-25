package SoftwareGoldenModel


class SoftwareGoldenModel (){

	def Whitening_sw(reset: Boolean, bitIn: Int, lfsr: String): (Int, String) = {
	   var len = 7
	   var buf = Array.ofDim[Int](len)
	   var bitOut: Int = 0
	   if (reset)
	      for (i <- 0 to len-1)
	         buf(i) = lfsr(i).asDigit
	   else  {
	      for (i <- 0 to len-1)  
	         buf(i) = lfsr(i).asDigit
	   
	   //for (i <- 0 to len-1) print(buf(i))
	   //println()
	      var temp = buf(6)
	      buf(6) = buf(5)
	      buf(5) = buf(4)
	      buf(4) = buf(3) ^ temp
	      buf(3) = buf(2) 
	      buf(2) = buf(1)
	      buf(1) = buf(0)
	      buf(0) = temp

	      bitOut = bitIn ^ temp
	   }
	   //for (i <- 0 to len-1) println(buf(i))
	   println("	       Software Output = "+ bitOut)
	   return (bitOut, buf.mkString(""))
	}


	def Crc_sw(reset: Boolean, bitIn: Int, lfsr: String): (String) = {
	   var len = 24
	   var buf = Array.ofDim[Int](len)
	   var bitOut: Int = 0
	   if (reset)
	      for (i <- 0 to len-1)
	        //buf(i) = lfsr(i).asDigit
	     	buf(i) = lfsr(len-1-i).asDigit 
	     	// position 23 shall be set as the MSB
	   else  {
	      for (i <- 0 to len-1)  
	         buf(i) = lfsr(i).asDigit
	   
	   //for (i <- 0 to len-1) print(buf(i))
	   //println()
	      var temp = buf(23) ^bitIn
	      buf(23) = buf(22)
	      buf(22) = buf(21)
	      buf(21) = buf(20)
	      buf(20) = buf(19)
	      buf(19) = buf(18)
	      buf(18) = buf(17)
	      buf(17) = buf(16)
	      buf(16) = buf(15)
	      buf(15) = buf(14)
	      buf(14) = buf(13)
	      buf(13) = buf(12)
	      buf(12) = buf(11)
	      buf(11) = buf(10)
	      buf(10) = buf(9) ^ temp
	      buf(9) = buf(8) ^ temp
	      buf(8) = buf(7)
	      buf(7) = buf(6)
	      buf(6) = buf(5) ^ temp
	      buf(5) = buf(4)
	      buf(4) = buf(3) ^ temp
	      buf(3) = buf(2) ^ temp
	      buf(2) = buf(1)
	      buf(1) = buf(0) ^ temp
	      buf(0) = temp
	   }
	   //for (i <- 0 to len-1) println(buf(i))
	  //println(s"Software Output = $buf")
		println("	       Software Output = " +buf(0)+buf(1)+buf(2)+buf(3)+buf(4)+buf(5)+buf(6)+buf(7)+buf(8)+buf(9)+buf(10)+buf(11)+buf(12)+buf(13)+buf(14)+buf(15)+buf(16)+buf(17)+buf(18)+buf(19)+buf(20)+buf(21)+buf(22)+buf(23))
	   return (buf.mkString(""))
	}

}