#define PACKET_ASSEMBLER_WRITE 0x2000
#define PACKET_ASSEMBLER_WRITE_COUNT 0x2008
#define PACKET_ASSEMBLER_READ 0x2100
#define PACKET_ASSEMBLER_READ_COUNT 0x2108

#include <stdio.h>
#include <inttypes.h>

#include "mmio.h"


uint16_t pack_PABundle(uint8_t trigger, uint8_t data) {
  return (trigger << 8)|data;
}


int main(void)
{
  uint64_t trigger = 1;
  uint64_t data_AA = 0x8E89BED6U;
//01101011011111011001000101110001;
  uint64_t data_pduH = 0x1002U; 
//0100000000001000;
  uint64_t data_pduAA = 0x0002723280C6U;
//011000110000000101001100010011100100000000000000;
  uint64_t data_pduData1 = 0x5543530806050102U;
//0100000010000000101000000110000000010000110010101100001010101010;
  uint64_t data_pduData2 = 0x334DU;
//1011001011001100;
  //uint64_t crc_seed = 010101010101010101010101;
  //uint64_t white_seed = 1100101;
  uint64_t done = 0;
  //uint64_t preamble = 01010101;
  uint8_t data_eight;
  uint8_t PA_out;

  for (int i = 0; i < 22; i++){
    if (i>=0 && i<4) {
	    data_eight = data_AA>>8*i;
        if (i==0) {
            printf("pack data: %#010x \n", pack_PABundle(1, data_eight));
            reg_write64(PACKET_ASSEMBLER_WRITE, pack_PABundle(1, data_eight));
            printf("pack data: %#010x \n", pack_PABundle(0, data_eight));            
            reg_write64(PACKET_ASSEMBLER_WRITE, pack_PABundle(0, data_eight));
        }
        else {
            printf("pack data: %#010x \n", pack_PABundle(0, data_eight));
            reg_write64(PACKET_ASSEMBLER_WRITE, pack_PABundle(0, data_eight));
        }
    }
    
    if (i>=4 && i<6) {
	data_eight = data_pduH>>8*(i-4);
    printf("pack data: %#010x \n", pack_PABundle(0, data_eight));            
    reg_write64(PACKET_ASSEMBLER_WRITE, pack_PABundle(0, data_eight));    
    }
    if (i>=6 && i<12) {
	data_eight = data_pduAA>>8*(i-6);
    printf("pack data: %#010x \n", pack_PABundle(0, data_eight));            
    reg_write64(PACKET_ASSEMBLER_WRITE, pack_PABundle(0, data_eight));
    }
    if (i>=12 && i<20) {
    data_eight = data_pduData1>>8*(i-12);
    printf("pack data: %#010x \n", pack_PABundle(0, data_eight));            
    reg_write64(PACKET_ASSEMBLER_WRITE, pack_PABundle(0, data_eight));
    }
    if (i>=20 && i<22) {
	data_eight = data_pduData2>>8*(i-20);
    printf("pack data: %#010x \n", pack_PABundle(0, data_eight));            
    reg_write64(PACKET_ASSEMBLER_WRITE, pack_PABundle(0, data_eight));
    }
    
  }

    while (1) {
	PA_out = reg_read64(PACKET_ASSEMBLER_READ);
	if (PA_out %2 == 0) {
		printf("%d", PA_out >> 1);
    } else {
		printf("%d\n", PA_out >> 1);
        break;
    }
    }
    return 0;
   
}
