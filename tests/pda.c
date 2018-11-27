#define PACKET_DISASSEMBLER_WRITE 0x2000
#define PACKET_DISASSEMBLER_WRITE_COUNT 0x2008
#define PACKET_DISASSEMBLER_READ 0x2100
#define PACKET_DISASSEMBLER_READ_COUNT 0x2108

#include <stdio.h>
#include <inttypes.h>

#include "mmio.h"


uint16_t pack_PDABundle(uint8_t trigger, uint8_t data) {
    //trigger here refers to switch actually, avoid cpp syntax conflicts
  return (trigger << 1)|data;
}

/*uint64_t convertToHex(uint64_t number) {
  uint64_t hexResult = 0;
  uint64_t hexChar[16]={0x0,0x1,0x2,0x3,0x4,0x5,0x6,0x7,0x8,0x9,0xa,0xb,0xc,0xd,0xe,0xf}; 
  while(number) {
      hexResult = hexChar[number & 0xf] << 4 + hexResult;
      number = (unsigned) number >> 4;
}
  return hexResult;
}*/

uint64_t convertToHex(uint64_t number) {
  uint64_t hexResult = 0x0;
  while(number) {
    if(number%16 <= 9) hexResult = hexResult<<4 + number%16;
    else if (number%16 == 10) hexResult = hexResult<<4 + 0xa;
    else if (number%16 == 11) hexResult = hexResult<<4 + 0xb;
    else if (number%16 == 12) hexResult = hexResult<<4 + 0xc;
    else if (number%16 == 13) hexResult = hexResult<<4 + 0xd;
    else if (number%16 == 14) hexResult = hexResult<<4 + 0xe;
    else hexResult = hexResult<<4 + 0xf;
    number = number >> 4;
  }
  return hexResult;
}

int main(void)
{
    //input: pre_preamble bpreamble baccess_address pdu_crc_whitened
  uint64_t trigger = 1;
  uint64_t data_pre_preamble = 000111U;
  uint64_t data_preamble = 0x55U;//2*4
  uint64_t data_AA = 0X6B7D9171U;
  uint64_t data_pdu_crc_whiten = 1111 0001 1011 1011 1000 1001 1000 0100 1111 0000 1010 1011 0010 0110 0000 1101 1110 1110 0000 1100 0010 1000 0111 0010 0111 1001 1010 0010 1000 0011 1100 1011 1010 0000 1100 0100 1110 1100 1110 1011U;//40*4
  

  uint8_t data;
  uint8_t PDA_out;

  for (int i = 0; i < 50; i++){
    if (i >= 0 && i < 8){
        data = (data_preamble >> (7-i)) & 1;
        if (i == 0) {
            printf("pack data: %#010x \n", pack_PDABundle(1, data));
            reg_write64(PACKET_DISASSEMBLER_WRITE, pack_PDABundle(1, data));
            printf("pack data: %#010x \n", pack_PDABundle(0, data));
            reg_write64(PACKET_DISASSEMBLER_WRITE, pack_PDABundle(0, data));
        }
        else {
            printf("pack data: %#010x \n", pack_PDABundle(0, data));
            reg_write64(PACKET_DISASSEMBLER_WRITE, pack_PDABundle(0, data));
        }
    }
    if (i >= 8 && i < 40) {
	    data = (data_AA >> (39-i)) & 1;
        printf("pack data: %#010x \n", pack_PDABundle(0, data));
        reg_write64(PACKET_DISASSEMBLER_WRITE, pack_PDABundle(0, data));
    }
    /*
    if (i>=10 && i<50) {
	data = (convertToHex(data_pdu_crc_whiten)>>(49-i+10)) & 1;
    printf("pack data: %#010x \n", pack_PDABundle(0, data));
    reg_write64(PACKET_DISASSEMBLER_WRITE, pack_PDABundle(0, data));
    }*/

  }

    while (1) {
	PDA_out = reg_read64(PACKET_DISASSEMBLER_READ);
	if (PDA_out %2 == 0) {
		printf("%d", PDA_out >> 14);
    } else {
		printf("%d\n", PDA_out >> 14);
        break;
    }
    }
    return 0;

}
