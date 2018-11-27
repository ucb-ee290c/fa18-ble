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
  //uint64_t done = 0;
  uint8_t data;
  uint8_t PDA_out;

  for (int i = 0; i < 367; i++){

    if (i>=0 && i<8) {
        data = (data_preamble>>(7-i)) & 1;
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
    if (i>=8 && i<40) {
	data = (data_AA>>(39-i+8)) & 1;
    printf("pack data: %#010x \n", pack_PDABundle(0, data));
    reg_write64(PACKET_DISASSEMBLER_WRITE, pack_PDABundle(0, data));
    }
    if (i>=40 && i<360) {
	data = (data__pdu_crc_whiten>>(359-i+40)) & 1;
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
