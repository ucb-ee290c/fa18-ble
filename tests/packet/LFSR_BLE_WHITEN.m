%% LFSR Implementation
% 24-bit LFSR X^24 + X^10 + X^9 + X^6 + X^4 + X^3 + X + 1
% as specified by BLE 5.0 page 2600
% original file based on https://www.mathworks.com/matlabcentral/fileexchange/60936-whitening--lfsr
% this was helpful https://www.allaboutcircuits.com/technical-articles/long-distance-bluetooth-low-energy-bit-data-paths/

%%

function [pdu_crc_whitened] = LFSR_BLE_WHITEN(pdu_crc, channel)

    channel_str=dec2bin(channel,6);
    
    channel_bin = zeros(1,numel(channel_str));
    for ii=1:numel(channel_str);
        if(channel_str(ii)=='1')
            channel_bin(ii)=1;
        elseif(channel_str(ii)=='0')
            channel_bin(ii)=0;
        else
            channel_bin(ii)=-1; % oops
        end
    end

    lfsr = [1 channel_bin]; % Initialize the LFSR with channel number
     
    pdu_crc_whitened=zeros(1,numel(pdu_crc));
    lfsr_next=zeros(1,numel(lfsr));
    for ii = 1:numel(pdu_crc)
                
        lfsr_next(1) = lfsr(7);                        % position 0
        lfsr_next(2) = lfsr(1);
        lfsr_next(3) = lfsr(2);
        lfsr_next(4) = lfsr(3);
        lfsr_next(5) = xor(lfsr(7), lfsr(4));
        lfsr_next(6) = lfsr(5);
        lfsr_next(7) = lfsr(6);                         % position 6                    
        pdu_crc_whitened(ii)=xor(pdu_crc(ii),lfsr(7));
        
        lfsr=lfsr_next;
    end

end
