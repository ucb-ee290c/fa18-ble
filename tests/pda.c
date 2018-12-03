#define PACKET_DISASSEMBLER_WRITE 0x2000
#define PACKET_DISASSEMBLER_WRITE_COUNT 0x2008
#define PACKET_DISASSEMBLER_READ 0x2100
#define PACKET_DISASSEMBLER_READ_COUNT 0x2108

#include <stdio.h>
#include <inttypes.h>

#include "mmio.h"


uint64_t pack_PDABundle(uint64_t trigger, uint64_t data) {
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
  int i = 0;
  while(number) {
    if(number%16 <= 9) hexResult = hexResult + (number%16)<<(4*i);
    else if (number%16 == 10) hexResult = hexResult + 0xa<<(4*i);
    else if (number%16 == 11) hexResult = hexResult + 0xb<<(4*i);
    else if (number%16 == 12) hexResult = hexResult + 0xc<<(4*i);
    else if (number%16 == 13) hexResult = hexResult + 0xd<<(4*i);
    else if (number%16 == 14) hexResult = hexResult + 0xe<<(4*i);
    else hexResult = hexResult + 0xf<<(4*i);
    number = number >> 4;
    i++;
  }
  return hexResult;
}

int main(void)
{
    //input: pre_preamble bpreamble baccess_address pdu_crc_whitened
  uint64_t trigger = 1;
  uint64_t data_preamble = 0x55U;//2,8
  uint64_t data_AA = 0X6B7D9171U;//8,32
  uint64_t data_pduH = 0xF1BBU;//4,16
  uint64_t data_pduAA = 0x8984F0AB260DU;//12,48
  uint64_t data_pduData1 = 0xEE0C28B279U;//10,40
  uint64_t data_pduData2 = 0xA283CBA0U;//8,32
  uint64_t data_crc = 0x748AADU;//6,24

  uint64_t data;
  uint64_t PDA_out;

  for (int i = 0; i < 200; i++){
    if (i >= 0 && i < 8){
        data = (data_preamble >> (7-i)) & 1;
        if (i == 0) {
            //printf("pack data: preamble %#001x \n", data);
            reg_write64(PACKET_DISASSEMBLER_WRITE, pack_PDABundle(1, data));
            printf("pack data: preamble %#001x \n", data);
            reg_write64(PACKET_DISASSEMBLER_WRITE, pack_PDABundle(0, data));
        }
        else {
            printf("pack data: preamble %#001x \n", data);
            reg_write64(PACKET_DISASSEMBLER_WRITE, pack_PDABundle(0, data));
        }
    }
    if (i >= 8 && i < 40) {
	    data = (data_AA >> (39-i)) & 1;
        printf("pack data: AA %#001x \n", data);
        reg_write64(PACKET_DISASSEMBLER_WRITE, pack_PDABundle(0, data));
    }
    if (i >= 40 && i < 56) {
        data = (data_pduH >> (55-i)) & 1;
        printf("pack data: pdu header %#001x \n", data);
        reg_write64(PACKET_DISASSEMBLER_WRITE, pack_PDABundle(0, data));
    }
    if (i >= 56 && i < 104){
        data = (data_pduAA >> (103-i)) & 1;
        printf("pack data: pdu AA %#001x \n", data);
        reg_write64(PACKET_DISASSEMBLER_WRITE, pack_PDABundle(0, data));
    }
    if (i >= 104 && i < 144){
        data = (data_pduData1 >> (143-i)) & 1;
        printf("pack data: pdu payload 1 %#001x \n", data);
        reg_write64(PACKET_DISASSEMBLER_WRITE, pack_PDABundle(0, data));
    }  
    if (i >= 144 && i < 176){
        data = (data_pduData2 >> (175-i)) & 1;
        printf("pack data: pdu payload 2 %#001x \n", data);
        reg_write64(PACKET_DISASSEMBLER_WRITE, pack_PDABundle(0, data));
    }
    if (i >= 176 && i < 200){
        data = (data_crc >> (199-i)) & 1;
        printf("pack data: crc %#001x \n", data);
        reg_write64(PACKET_DISASSEMBLER_WRITE, pack_PDABundle(0, data));
    }               

  }

    while (1) {
        PDA_out = reg_read64(PACKET_DISASSEMBLER_READ);
        if (PDA_out %2 == 0) {
            printf("unpack data: %#002x \n", PDA_out >> 11);
            if((PDA_out >> 1) % 2 == 1){
                printf("CRC Invalid\n");
            }
            if((PDA_out >> 2) % 2 == 1){
                printf("AA Invalid\n");
            }
        } else {
            printf("unpack data: %#002x \n", PDA_out >> 11);
            if((PDA_out >> 1) % 2 == 1){
                printf("CRC Invalid\n");
            }
            if((PDA_out >> 2) % 2 == 1){
                printf("AA Invalid\n");
            }
            printf("Finished disassembling \n");
            break;
        }
    }

    // for(int i= 0; i < 24; i++)
    // {
    //     PDA_out = reg_read64(PACKET_DISASSEMBLER_READ);
    //     printf("unpack data: %#002x \n", PDA_out >> 14);
    // }    
    return 0;

}
