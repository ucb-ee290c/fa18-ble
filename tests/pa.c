#define CORDIC_WRITE 0x2000
#define CORDIC_WRITE_COUNT 0x2008
#define CORDIC_READ 0x2100
#define CORDIC_READ_COUNT 0x2108

#include <stdio.h>
#include <inttypes.h>

#include "mmio.h"


uint64_t pack_PABundle(uint64_t trigger, uint64_t data, uint64_t crc_seed, uint64_t white_seed, uint64_t done) {
  return
    trigger << 40       |
    data << 32          |
    crc_seed << 8       |
    white_seed << 1     |
    done;
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
  uint64_t AA = 01101011011111011001000101110001;
  uint64_t crc_seed = 010101010101010101010101;
  uint64_t white_seed = 1100101;
  uint64_t done = 0;
  //uint64_t preamble = 01010101;

  uint8_t AA_eight;
  for (int i = 0; i < 4; i++ ){
    AA_eight = AA>>8*(3-i);
    reg_write64(CORDIC_WRITE, pack_PABundle(trigger, AA_eight, crc_seed, white_seed, done));
    while(reg_read64(CORDIC_READ)){
    	printf("unpack data: %d", reg_read64(CORDIC_READ)"\n");
    }
  }

	return 0;
}
