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


int main(void)
{
    //input: pre_preamble bpreamble baccess_address pdu_crc_whitened
  uint64_t trigger = 1;
  uint64_t data_pre_preamble = 0b000111U;
  uint64_t data_preamble = 0b0101_0101U;
  uint64_t data_AA = 0b0110_1011_0111_1101_1001_0001_0111_0001U;
  uint64_t data_pdu_crc_whiten = 0b1111_0001_1011_1011_1000_1001_1000_0100_1111_0000_1010_10110010011000001101111011100000110000101000011100100111100110100010100000111100101110100000110001001110110011101011U;

  //output:
  uint64_t done = 0;
  uint8_t data_eight;
  uint8_t PDA_out;

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
    if (i>=12 && i<17) {
    data_eight = data_pduData1>>8*(i-12);
    printf("pack data: %#010x \n", pack_PABundle(0, data_eight));
    reg_write64(PACKET_ASSEMBLER_WRITE, pack_PABundle(0, data_eight));
    }
    if (i>=17 && i<21) {
	data_eight = data_pduData2>>8*(i-17);
    printf("pack data: %#010x \n", pack_PABundle(0, data_eight));
    reg_write64(PACKET_ASSEMBLER_WRITE, pack_PABundle(0, data_eight));
    }

  }

    while (1) {
	PDA_out = reg_read64(PACKET_DISASSEMBLER_READ);
	if (PDA_out %2 == 0) {
		printf("%d", PA_out >> 1);
    } else {
		printf("%d\n", PA_out >> 1);
        break;
    }
    }
    return 0;

}
