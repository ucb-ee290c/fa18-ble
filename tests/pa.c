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

int getDigits(uint64_t number) { //count the number of digits for each data. e.g, data_AA should be 8.
  int count = 0;
  while (number) {
     count++;
     number = number >> 1;
  }
  return count;
}

int main(void)
{
  uint64_t trigger = 1;
  uint64_t data_AA = 0x8E89BED6U;
//  uint64_t data_pduH = 0x1002U;
  uint64_t data_pduH = 0x0F02U;  //first two hex numbers indicate the length of payload
  uint64_t data_pduAA = 0x0002723280C6U;
  uint64_t data_pduData1 = 0x0806050102U; //payload 1
//  uint64_t data_pduData2 = 0x334D554353U; 
  uint64_t data_pduData2 = 0x43303932U; //payload 2
  
  //interactive interface (optional)
  /*printf("Enter data_AA in hex: (End with U)");
  scanf("%x", &data_AA);
  printf("Enter data_pduH in hex: (End with U)");
  scanf("%x", &data_pduH);
  printf("Enter data_pduAA in hex: (End with U)");
  scanf("%x", &data_pduAA);
  printf("Enter data_pduData1 in hex: (End with U)");
  scanf("%x", &data_pduData1);
  printf("Enter data_pduData2 in hex: (End with U)");
  scanf("%x", &data_pduData2);*/

  int digits_data_AA = getDigits(data_AA);
  int digits_data_pduH = getDigits(data_pduH);
  int digits_data_pduAA = getDigits(data_pudAA);
  int digits_data_pduData1 = getDigits(data_pduData1);
  int digits_data_pduData2 = getDigits(data_pduData2);

  uint64_t done = 0;
  uint8_t data_eight;
  uint8_t PA_out;
  
  for (int i = 0; i < 21; i++){
    if (i>=0 && i<digits_data_AA/2) {//original [0,4)
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
    
    if (i>=digits_data_AA/2 && i<digits_data_AA/2+digits_data_pduH/2) {//original [4,6)
	data_eight = data_pduH>>8*(i-digits_data_AA/2);
    printf("pack data: %#010x \n", pack_PABundle(0, data_eight));            
    reg_write64(PACKET_ASSEMBLER_WRITE, pack_PABundle(0, data_eight));    
    }
    if (i>=digits_data_AA/2+digits_data_pduH/2 && i<digits_data_AA/2+digits_data_pduH/2+digits_data_pduAA/2) {//original [6,12)
	data_eight = data_pduAA>>8*(i-(digits_data_AA/2+digits_data_pduH/2));
    printf("pack data: %#010x \n", pack_PABundle(0, data_eight));            
    reg_write64(PACKET_ASSEMBLER_WRITE, pack_PABundle(0, data_eight));
    }
    if (i>=digits_data_AA/2+digits_data_pduH/2+digits_data_pduAA/2 && i<digits_data_AA/2+digits_data_pduH/2+digits_data_pduAA/2+digits_data_pduData1/2) {//original [12,17)
    data_eight = data_pduData1>>8*(i-(digits_data_AA/2+digits_data_pduH/2+digits_data_pduAA/2));
    printf("pack data: %#010x \n", pack_PABundle(0, data_eight));            
    reg_write64(PACKET_ASSEMBLER_WRITE, pack_PABundle(0, data_eight));
    }
    if (i>=digits_data_AA/2+digits_data_pduH/2+digits_data_pduAA/2+digits_data_pduData1/2 && i<digits_data_AA/2+digits_data_pduH/2+digits_data_pduAA/2+digits_data_pduData1/2+digits_data_pduData2/2) {
	data_eight = data_pduData2>>8*(i-(digits_data_AA/2+digits_data_pduH/2+digits_data_pduAA/2+digits_data_pduData1/2));//original [17,21)
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
