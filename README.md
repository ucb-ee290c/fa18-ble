# EE290C Bluetooth Low Energy Baseband

[![Build Status](https://travis-ci.org/ucberkeley-ee290c/fa18-ble.svg?branch=master)](https://travis-ci.org/ucberkeley-ee290c/fa18-ble)

This Documentation is for Bluetooth Low Energy (BLE) Baseband group work in EE290C @ UC Berkeley 2018 fall semester.

## Course Description
2018 Fall EE290C, taught by Prof. Borivoje Nikolic, offers Advanced Topics in Circuit Design: VLSI Signal Processing. The course adopts Chisel, an open-source hardware construction language developed at UC Berkeley, to implement digital signal processing designs. The design could be verified by hooked up to the Rocket Chip. Our group chose to implement a BLE baseband for the final project.
<br>

## Project Overview
The BLE baseband we implemented includes two parts: packet assmebler (PA) and disassmbler (PDA), which are responsible for TX and RX sides respectively. Two submodules, CRC and whitening, are attached to PA/PDA to follow Bluetooth Specification v5.0. The final goal is to implement a complete BLE baseband chain. 

The diagram of the expected BLE loop chain is shown below:
![blockDiagram](doc/image/loopback_chain.png)
<br>

## Team Members
Jerry Duan, Mingying Xie, and Yalun Zheng
<br>

## Tape-in 1
- Update PacketAssembler to new Chisel standard and connect to RocketChip
- Build PA Chain and insert FIFOs for testing
- Construct C tests to verify the functionality

## Tape-in 2
- Improve packet transmitting effeciency (delete CRC_seed and white_seed in PA input bundle)
- Complete PA chain and the output matches software golden model
- Similar work as tape-in1 has been done to PacketDisAssembler (PDA chain, C tests)

## Modules
1) PA: 
[packet assembler](https://github.com/ucberkeley-ee290c/fa18-ble/tree/master/doc/pa.md), 
[PA chain](https://github.com/ucberkeley-ee290c/fa18-ble/tree/master/doc/pa_chain.md)
2) PDA: 
[packet disassembler](https://github.com/ucberkeley-ee290c/fa18-ble/tree/master/doc/pda.md), 
[PDA chain](https://github.com/ucberkeley-ee290c/fa18-ble/tree/master/doc/pda_chain.md)
3) Top level: 
[loopback](https://github.com/ucberkeley-ee290c/fa18-ble/tree/master/doc/loop.md)
4) CRC: 
[CRC](https://github.com/ucberkeley-ee290c/fa18-ble/tree/master/doc/crc.md)
5) Whitening: 
[Whitening](https://github.com/ucberkeley-ee290c/fa18-ble/tree/master/doc/whitening.md)
<br>

## Working Principle
### BLE packet structure
![blockDiagram](doc/image/ble_packet.png)


### TODO
- Add-on features like FEC mentioned in Bluetooth 5 Spec.

- DMA implementation after C tests verifying the functionality

### Acknowledgement
Here is our appreciation to Prof. Borivoje Nikolic, Prof. Kristofer Pister and the GSI Paul Rigge for guiding us in this project. Their valuable suggestions and feedback help us move forward. Also the work from last semester's group inspired us greatly and here is their tape-out (https://github.com/tapeout/ble-baseband). Lastly, we would like to thank David Burnett and Rachel Zoll for helping us get on board and explain the former BLE stucture and tests.

