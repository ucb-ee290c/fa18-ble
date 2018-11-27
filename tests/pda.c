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

uint64_t convertToHex(uint64_t number) {
  uint64_t hexResult = 0;
  uint64_t hexChar[16]={0x0,0x1,0x2,0x3,0x4,0x5,0x6,0x7,0x8,0x9,0xa,0xb,0xc,0xd,0xe,0xf}; 
  while(number) {
      hexResult = hexChar[number & 0xf] + hexResult << 4;
      number = (unsigned) number >> 4;
}
  return hexResult;
}

int main(void)
{
    //input: pre_preamble bpreamble baccess_address pdu_crc_whitened
  uint64_t trigger = 1;
  uint64_t data_pre_preamble = 000111U;
  uint64_t data_preamble = 01010101U;//2*4
  uint64_t data_AA = 01101011011111011001000101110001U;//8*4
  uint64_t data_pdu_crc_whiten = 1111000110111011100010011000010011110000101010110010011000001101111011100000110000101000011100100111100110100010100000111100101110100000110001001110110011101011U;//40*4

  //output:
  //uint64_t done = 0;
  uint8_t data;
  uint8_t PDA_out;

  for (int i = 0; i < 50; i++){

    if (i>=0 && i<2) {
        data = (convertToHex(data_preamble)>>(7-i)) & 1;
        if (i==0) {
            printf("pack data: %#010x \n", pack_PDABundle(1, data));
            reg_write8(PACKET_DISASSEMBLER_WRITE, pack_PDABundle(1, data));
            printf("pack data: %#010x \n", pack_PDABundle(0, data));
            reg_write8(PACKET_DISASSEMBLER_WRITE, pack_PDABundle(0, data));
        }
        else {
            printf("pack data: %#010x \n", pack_PDABundle(0, data));
            reg_write64(PACKET_DISASSEMBLER_WRITE, pack_PDABundle(0, data));
        }
    }
    if (i>=2 && i<10) {
	data = (convertToHex(data_AA)>>(9-i+2)) & 1;
    printf("pack data: %#010x \n", pack_PDABundle(0, data));
    reg_write64(PACKET_DISASSEMBLER_WRITE, pack_PDABundle(0, data));
    }
    if (i>=10 && i<50) {
	data = (convertToHex(data__pdu_crc_whiten)>>(49-i+10)) & 1;
    printf("pack data: %#010x \n", pack_PDABundle(0, data));
    reg_write64(PACKET_DISASSEMBLER_WRITE, pack_PDABundle(0, data));
    }

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
