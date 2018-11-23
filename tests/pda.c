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
  uint64_t data_pre_preamble = 000111U;
  uint64_t data_preamble = 01010101U;
  uint64_t data_AA = 01101011011111011001000101110001U;//8*4
  uint64_t data_pdu_crc_whiten = 1111000110111011100010011000010011110000101010110010011000001101111011100000110000101000011100100111100110100010100000111100101110100000110001001110110011101011U;//40*4

  //output:
  uint64_t done = 0;
  uint8_t data_eight;
  uint8_t PDA_out;

  for (int i = 0; i < 21; i++){
    if (i>=0 && i<3) {
	    data_eight = data_AA>>2*i;//How to group them? 2 in this case
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

    if (i>=3 && i<7) {
	data_eight = data_pduH>>2*(i-3);
    printf("pack data: %#010x \n", pack_PABundle(0, data_eight));
    reg_write64(PACKET_ASSEMBLER_WRITE, pack_PABundle(0, data_eight));
    }
    if (i>=7 && i<23) {
	data_eight = data_pduAA>>2*(i-7);
    printf("pack data: %#010x \n", pack_PABundle(0, data_eight));
    reg_write64(PACKET_ASSEMBLER_WRITE, pack_PABundle(0, data_eight));
    }
    if (i>=23 && i<103) {
    data_eight = data_pduData1>>2*(i-23);
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
