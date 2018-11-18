function [I,Q,packet01] = generate_packet_ble_v2(Fs,num_payload_bytes,num_preambles)

Fs=250e6;
% Chip period
T = 1/1e6;
dT = 1/Fs;
dt=dT;

%% Begin Packet Formation

pre_preamble = [0 0 0 1 1 1]; % found in recorded packet; seems like a good idea

bpreamble = [0 1 0 1 0 1 0 1]; % since the access address ends with a 0 (AA LSB = 0)

% http://j2abro.blogspot.com/2014/06/understanding-bluetooth-advertising.html
% standard AA for broadcast packets is 0x8E89BED6

baccess_address = [0 1 1 0 1 0 1 1 0 1 1 1 1 1 0 1 1 0 0 1 0 0 0 1 0 1 1 1 0 0 0 1];
% fliplr(dec2bin(hex2dec('8E89BED6'),32)) -- there's a fliplr so LSB is first

% transmit on BLE channels 37, 38, 39: 2.402GHz, 2.426GHz, and 2.480GHz,
% respectively
% http://www.argenox.com/a-ble-advertising-primer/

%% PDU header

PDU_type = [0 1 0 0]; % 4 bytes. 0010b is "ADV_NONCONN_IND"; tx only mode
% must be flipped wrt table below so LSB comes first

%  PDU Types
%  b3b2b1b0 Packet Name
%  0000 ADV_IND? connectable undirected advertising event
%  0001 ADV_DIRECT_IND?connectable directed advertising event
%  0010 ADV_NONCONN_IND?non-connectable undirected advertising event
%  0011 SCAN_REQ?scan request
%  0100 SCAN_RSP: scan response
%  0101 CONNECT_REQ?connection request
%  0110 ADV_SCAN_IND?scannable undirected advertising event
%  0111-1111 Reserved

RFU=[0 0]; % reserved for future use
TxAdd=0; % 0 means legit address, 1 means random address
RxAdd=0; % not sure what these are for
% length_PDU=[1 1 1 1 0 0 0 0]; % 8 bits in BLE 5. 6 for address, 3 for headers, 6 for name payload. fliplr(dec2bin(15,8))
% length_PDU=[1 0 1 0 0 1 0 0]; % 8 bits in BLE 5. max typical length 37. fliplr(dec2bin(37,8))
length_PDU=[0 0 0 0 1 0 0 0]; % 8 bits in BLE 5. fliplr(dec2bin(16,8))

pdu_header= [PDU_type RFU TxAdd RxAdd length_PDU];


%% payload

% Since we used 0010 PDU type, payload is divided into two parts:
% 6 bits: advertiser address
% 0-31 bytes: optional advertiser data

% AdvA = [1 0 0 1 1 0 0 1 0 1 0 0 1 0 0 1 1 0 0 0 1 1 0 1 1 1 0 1 0 1 1 1 1 1 1 0 1 0 1 1 0 0 0 0 1 0 0 1]; 
% fliplr(dec2bin(hex2dec('90d7ebb19299'),48)) -- there's a fliplr so LSB is first
% advertiser MAC address, 6 bits. here set to 0x90d7ebb19299, random copied from http://processors.wiki.ti.com/index.php/BLE_sniffer_guide

AdvA = [0 1 1 0 0 0 1 1 0 0 0 0 0 0 0 1 0 1 0 0 1 1 0 0 0 1 0 0 1 1 1 0 0 1 0 0 0 0 0 0 0 0 0 0 0 0 0 0];
% whatever I got from the iogear dongle, LSB first

%% data payload, first part: length, GAP value, data. 3 bytes/octets used here; 1 for data

payload1=[
    0 1 0 0 0 0 0 0 ... % AdvData length 2, LSB first
    1 0 0 0 0 0 0 0 ... % AdvData GAP code 0x01 ("flags"), LSB first, from https://www.bluetooth.com/specifications/assigned-numbers/generic-access-profile
    1 0 1 0 0 0 0 0 ... % AdvData data 5, LSB first. for why 5, see Core Specification Supplement part A section 1.3 at https://www.bluetooth.com/specifications/bluetooth-core-specification
];

%% data payload, second part: length, GAP value, data. 28 bytes/octets used here; 26 for data.

payload2_header=[
    0 1 1 0 0 0 0 0 ... % AdvData length 6, LSB first
    0 0 0 1 0 0 0 0 ... % AdvData GAP code 0x08 ("short name"), LSB first
   % 5 bytes of ASCII data to be appended next   
];

% dec2bin(int8('A'),8) dec2bin(int8('B'),8) etc. up to 26-byte limit: A to Z
% value_seq = fliplr(dec2bin(int8('A'):int8('A')+25,8));
% value_seq = [fliplr(dec2bin(int8('S'),8)) fliplr(dec2bin(int8('C'),8)) fliplr(dec2bin(int8('U'),8)) fliplr(dec2bin(int8('M'),8)) fliplr(dec2bin(int8('3'),8)) ];
value_seq = [fliplr(dec2bin(int8('S'),8)) fliplr(dec2bin(int8('C'),8)) fliplr(dec2bin(int8('A'),8)) fliplr(dec2bin(int8('M'),8)) fliplr(dec2bin(int8('3'),8)) ];

payload2_data = zeros(1,numel(value_seq));
for ii=1:numel(value_seq)
    if(value_seq(ii)=='1')
        payload2_data(ii)=1;
    elseif(value_seq(ii)=='0')
        payload2_data(ii)=0;
    else
        payload2_data(ii)=-1; % oops
    end
end

% load pay3.mat
% pay3=pay3(1:21);
% payload3_str=[];
% for ii=1:numel(pay3)
%     payload3_str=[payload3_str fliplr(dec2bin(pay3(ii),8))];
% end
% % payload3_str=fliplr(dec2bin(pay3))'; % junk that was in recorded packet idk why?
% payload3 = zeros(1,numel(value_seq));
% for ii=1:numel(payload3_str);
%     if(payload3_str(ii)=='1')
%         payload3(ii)=1;
%     elseif(payload3_str(ii)=='0')
%         payload3(ii)=0;
%     else
%         payload3(ii)=-1; % oops
%     end
% end

% pdu=[pdu_header AdvA payload1 payload2_header payload2_data payload3];
pdu=[pdu_header AdvA payload1 payload2_header payload2_data];

% then whiten payload and CRC, pg 2601 
% then loop every 100ms

crc=fliplr(LFSR_BLE_CRC(pdu)); % 3 bytes

adv_channel=37;
pdu_crc_whitened=LFSR_BLE_WHITEN([pdu crc],adv_channel);

packet01=[pre_preamble bpreamble baccess_address pdu_crc_whitened]; % LSB-first

% load recov_packet_scum3.mat
% packet01=[ 0 0 0 1 1 1 recov_packet];

% Convert packet to +/- 1
packet = 2 * packet01 - 1;
bits=1*packet; 

%% Weaver


%% setup

wif=2*pi*2.5e6; % 2.5MHz IF
fmod=.25e6; wmod=2*pi*fmod; % +/- 250kHz
fdata=1e6; % need freq not natural freq b/c this value doesn't generate a cosine
wrf=2*pi*0.05e9; % 50MHz for now
duration=numel(bits)/fdata;

% dt=1/250e6; % Agilent AWG runs at 250MHz
t=0:dt:duration-dt;

% FFT generals
L=size(t,2);
f=[(-1/dt)*(L/2-1:-1:0)/L (1/dt)*(1:1:L/2)/L];



%% build w(t) from bits

% this section turns base bit sequence into bits sampled at dt

if(1) % abrupt freq changes
    pt=[]; 
    for ii=1:numel(bits)
        pt=[pt bits(ii)*ones(1,1/(dt*fdata))];
    end
    dwdt=pt*wmod;
end

if(0) % half-sine shaping, probably wrong
    pt=[];
    for ii=1:numel(bits)
        pt=[pt bits(ii)*sin(2*pi*fdata/2*(0:dt:1/fdata-dt))];
    end
end

if(0) % abrupt freq changes w/ gaussian filter
    pt=zeros(1,numel(bits));
    for ii=1:numel(bits)
        pt=[pt bits(ii)*ones(1,1/(dt*fdata))];
    end
    dwdt=pt*wmod;
    HG=gaussdesign(1,4,1/(dt*fdata));
    dwdt=filtfilt(HG,1,dwdt);
end



%% generate signals

amp_noise=0*randn(size(t));
freq_noise=0*rand(size(t));

% pure tone
% s1=cos((wif+freq_noise).*t) + amp_noise; % pure tone at wif

% FSK signal: wif+/-wmod
% need to fix phase discontinuities

phicorr=2*pi*rand(1); % start with random phase
phi(1)=(wif+wmod*pt(1))*t(1) + phicorr(1) + freq_noise(1);

for ii=2:numel(t)
    % accumulate phase offset to correct phase discontinuities that would
    % otherwise result from changing frequency suddenly
    if (pt(ii)==-1 && pt(ii-1)==1) % 1 to 0 transition
        phicorr(ii)=phicorr(ii-1)+2*wmod*t(ii); % result of on-paper math to work
        % out how to get phases to line up with changing frequencies
    elseif(pt(ii)==1 && pt(ii-1)==-1) % 0 to 1 transition
        phicorr(ii)=phicorr(ii-1)-2*wmod*t(ii);
    else
        phicorr(ii)=phicorr(ii-1);
    end
    
    phi(ii)=(wif+wmod*pt(ii))*t(ii) + phicorr(ii) + freq_noise(ii);

end

% s1=cos(phi) + amp_noise;
s1=cos((wif+dwdt).*t+phicorr);
%% Weaver step 1: downconvert

idown=cos(1*wif*t);
qdown=cos(1*wif*t+pi/2);
s2=s1.*idown;
s3=s1.*qdown;

if(0) % double-sided FFT
%     figure; plot(t,s2);
    
    Fy=fft(s3);
    P1=abs(Fy/L);
    P2b=P1(1:floor(L/2)+1);
    P2a=P1(floor(L/2)+2:end);
    P3=[P2a P2b];
    figure; plot(f/1e6,10*log10(P3)); grid on; xlim([-10 10]);
end



%% Weaver step 2: filter

% Ideally we'd have a brick wall at wif so the upper mixing product is
% entirely cancelled.

% s4 and s5 are the waveforms we want to output to the function generator

lpf=fir1(500,wif*dt/pi);
%load lpf.mat;
s4=filtfilt(lpf,1,s2);
s5=filtfilt(lpf,1,s3);

if(0) % double-sided FFT
%     figure; plot(t,s5);
    
    Fy=fft(s4);
    P1=abs(Fy/L);
    P2b=P1(1:floor(L/2)+1);
    P2a=P1(floor(L/2)+2:end);
    P3=[P2a P2b]; % this line and the two above it are fftshift() oops
    figure; plot(f/1e6,10*log10(P3)); grid on; xlim([-20 20]);
end

%% Weaver step 3: upconvert & difference
% This is just to see what we should expect on the output of the Rohde; the
% Rohde will do this operation for us (mix, and subtract Q from I)

% we already have irf and qrf from the direct upconvert test section, so:
% s6=s4.*irf+s5.*qrf;

if(0) % double-sided FFT
    figure; plot(t,s6);
    
    Fy=fft(s6);
    P1=abs(Fy/L);
    P2b=P1(1:floor(L/2)+1);
    P2a=P1(floor(L/2)+2:end);
    P3=[P2a P2b];
    figure; plot(f/1e6,10*log10(P3)); grid on; %xlim([-20 20]);
end

I=s4-mean(s4);
Q=s5-mean(s5);

fullmemI=zeros(1,.5e6);
fullmemQ=zeros(1,.5e6);
fullmemI(1:numel(I))=I;
fullmemQ(1:numel(Q))=Q;
I=single(fullmemI);
Q=single(fullmemQ);

end

