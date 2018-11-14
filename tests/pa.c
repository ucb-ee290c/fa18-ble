#define CORDIC_WRITE 0x2000
#define CORDIC_WRITE_COUNT 0x2008
#define CORDIC_READ 0x2100
#define CORDIC_READ_COUNT 0x2108

#include <stdio.h>
#include <inttypes.h>

#include "mmio.h"


uint64_t pack_PABundle(uint64_t trigger, uint64_t data) {
  return (trigger << 8)|data;
}

uint64_t pack_out(uint64_t in, uint64_t done) {
    return (done << 1)|in; 
}

int64_t unpack_trigger(uint64_t packed) {
  return packed[40];
}

int64_t unpack_data(uint64_t packed) {
  return (packed >> 32) && 11111111;
}

int64_t unpack_crc_seed(uint64_t packed) {
  return packed >> 8 && 111111111111111111111111;
}

int64_t unpack_white_seed(uint64_t packed) {
  return packed >> 1 && 1111111;
}

int64_t unpack_done(uint64_t packed) {
  return packed[0];
}


int main(void)
{
  uint64_t trigger = 1;
  uint64_t data1 = 0110101101111101100100010111000101000000000010000110001100000001;
  uint64_t data2 = 0100110001001110010000000000000001000000100000001010000001100000;
  uint64_t data3 = 000100001100101011000010101010101011001011001100;
  //uint64_t crc_seed = 010101010101010101010101;
  //uint64_t white_seed = 1100101;
  uint64_t done = 0;
  //uint64_t preamble = 01010101;

  uint8_t data_eight;
  uint8_t PA_out;
  for (int i = 0; i < 22; i++ ){
    if (i>=0 && i<8) {
	data_eight = data1>>8*(7-i);
    }
    if (i>=8 && i<16) {
	data_eight = data>>8*(7-(i-8));
    }
    if (i>=16 && i<22) {
	data_eight = data>>8*(7-(i-16));
    }
    reg_write64(CORDIC_WRITE, pack_PABundle(trigger, data_eight));
    }
   int j=0;
   while (j<176) {
	PA_out = reg_read64(CORDIC_READ);
	if (PA_out%2 == 0) {
		printf("unpack data: %d", PA_out/2);
        } else {
		printf("\n");
        }
        j++;
    }
  

	return 0;
}
