#define PACKET_ASSEMBLER_WRITE 0x2000
#define PACKET_ASSEMBLER_WRITE_COUNT 0x2008
#define PACKET_ASSEMBLER_READ 0x2100
#define PACKET_ASSEMBLER_READ_COUNT 0x2108

#define PACKET_DISASSEMBLER_WRITE 0x200c
#define PACKET_DISASSEMBLER_WRITE_COUNT 0x2010
#define PACKET_DISASSEMBLER_READ 0x2100
#define PACKET_DISASSEMBLER_READ_COUNT 0x2108

#include <stdio.h>
#include <inttypes.h>

#include "mmio.h"

uint16_t pack_PABundle(uint8_t trigger, uint8_t data) {
  return (trigger << 8) | data;
}

int getDigits(uint64_t number) { //count the number of digits for each data. e.g, data_AA should be 8.
  int num =  number >> 8; //get the leftmost two hex
  int i = 1;
  int digits = 0;
  while (num) {
     if (num & 1) digits = digits + i; //check the last bit
     i = i*2;
     num = num >> 1;
  }
  return 2*digits;//convert byte to bit
}

int main(void)
{
  uint64_t trigger = 1;
  uint64_t data_AA = 0x8E89BED6U; //access address, constant
//  uint64_t data_pduH = 0x0F02U;  //first two hex numbers indicate the length of payload
  uint64_t data_pduH = 0x1502U; 
  uint64_t data_pduAA = 0x0002723280C6U; //advertising address, constant
  uint64_t data_pduData1 = 0x050102U; //payload 1, constant
//  uint64_t data_pduData2 = 0x433039320805U; //payload 2
  uint64_t data_pduData2 = 0x61726F420805U; //payload 2



  //interactive interface (optional)
//   printf("Enter data_AA in hex: (End with U)");
//   scanf("%x", &data_AA);
//   printf("Enter data_pduH in hex: (End with U)");
//   scanf("%x", &data_pduH);
//   printf("Enter data_pduAA in hex: (End with U)");
//   scanf("%x", &data_pduAA);
//   printf("Enter data_pduData1 in hex: (End with U)");
//   scanf("%x", &data_pduData1);
//   printf("Enter data_pduData2 in hex: (End with U)");
//   scanf("%x", &data_pduData2);

  int digits_data_AA = 8;
  int digits_data_pduH = 4;
  int digits_data_pduAA = 12;
  int digits_data_pduData1 = 6;
  int digits_data_pduData2 = getDigits(data_pduH) - digits_data_pduAA - digits_data_pduData1; //Use the leftmost two hex to calculate total digits of pdu
  //int digits_data_pduData2 = 8;

  //printf("PDU Length: %d", digits_data_pduData2);
 
  uint64_t data_eight;
  uint64_t PA_out;
  uint64_t PDA_out;
  
  for (int i = 0; i < digits_data_AA/2+digits_data_pduH/2+digits_data_pduAA/2+digits_data_pduData1/2+digits_data_pduData2/2; i++){
    if (i>=0 && i<digits_data_AA/2) {//original [0,4)
	    data_eight = data_AA>>8*i;
        if (i==0) {
            //printf("pack data: %#002x \n", pack_PABundle(1, data_eight));
            reg_write64(PACKET_ASSEMBLER_WRITE, pack_PABundle(1, data_eight));
            printf("pack data: %#002x \n", pack_PABundle(0, data_eight));            
            reg_write64(PACKET_ASSEMBLER_WRITE, pack_PABundle(0, data_eight));
        }
        else {
            printf("pack data: %#002x \n", pack_PABundle(0, data_eight));
            reg_write64(PACKET_ASSEMBLER_WRITE, pack_PABundle(0, data_eight));
        }
    }
    
    if (i>=digits_data_AA/2 && i<digits_data_AA/2+digits_data_pduH/2) {//original [4,6)
	data_eight = data_pduH>>8*(i-digits_data_AA/2);
    printf("pack data: %#002x \n", pack_PABundle(0, data_eight));            
    reg_write64(PACKET_ASSEMBLER_WRITE, pack_PABundle(0, data_eight));    
    }
    if (i>=digits_data_AA/2+digits_data_pduH/2 && i<digits_data_AA/2+digits_data_pduH/2+digits_data_pduAA/2) {//original [6,12)
	data_eight = data_pduAA>>8*(i-(digits_data_AA/2+digits_data_pduH/2));
    printf("pack data: %#002x \n", pack_PABundle(0, data_eight));            
    reg_write64(PACKET_ASSEMBLER_WRITE, pack_PABundle(0, data_eight));
    }
    if (i>=digits_data_AA/2+digits_data_pduH/2+digits_data_pduAA/2 && i<digits_data_AA/2+digits_data_pduH/2+digits_data_pduAA/2+digits_data_pduData1/2) {//original [12,17)
    data_eight = data_pduData1>>8*(i-(digits_data_AA/2+digits_data_pduH/2+digits_data_pduAA/2));
    printf("pack data: %#002x \n", pack_PABundle(0, data_eight));            
    reg_write64(PACKET_ASSEMBLER_WRITE, pack_PABundle(0, data_eight));
    }
    if (i>=digits_data_AA/2+digits_data_pduH/2+digits_data_pduAA/2+digits_data_pduData1/2 && i<digits_data_AA/2+digits_data_pduH/2+digits_data_pduAA/2+digits_data_pduData1/2+digits_data_pduData2/2) {
	data_eight = data_pduData2>>8*(i-(digits_data_AA/2+digits_data_pduH/2+digits_data_pduAA/2+digits_data_pduData1/2));//original [17,21)
    printf("pack data: %#002x \n", pack_PABundle(0, data_eight));            
    reg_write64(PACKET_ASSEMBLER_WRITE, pack_PABundle(0, data_eight));
    }
    
  }

    for(int i= 0; i < 24; i++)
    {
        PDA_out = reg_read64(PACKET_DISASSEMBLER_READ);
        printf("unpack data: %#002x \n", PDA_out);
    }
    return 0;
   
}
