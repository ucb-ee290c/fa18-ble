%% LFSR Implementation
% 24-bit LFSR X^24 + X^10 + X^9 + X^6 + X^4 + X^3 + X + 1
% as specified by BLE 5.0 page 2600
% this was helpful but init in animation is backwards https://www.allaboutcircuits.com/technical-articles/long-distance-bluetooth-low-energy-bit-data-paths/

%%

% function [lfsr, lfsr_dec] = LFSR_BLE_CRC(ble_input)
function [lfsr] = LFSR_BLE_CRC(ble_input)

    lfsr = [1 0 1 0 1 0 1 0 1 0 1 0 1 0 1 0 1 0 1 0 1 0 1 0]; % Initialize the LFSR with 0x555555 per BLE
        
    lfsr_next=zeros(1,numel(lfsr));
    for ii = 1:numel(ble_input)
        
        common=xor(lfsr(24),ble_input(ii));
        
        lfsr_next(1) = common;                        % position 0
        lfsr_next(2) = xor(common, lfsr(1));
        lfsr_next(3) = lfsr(2);
        lfsr_next(4) = xor(common, lfsr(3));
        lfsr_next(5) = xor(common, lfsr(4));
        lfsr_next(6) = lfsr(5);
        lfsr_next(7) = xor(common, lfsr(6));
        lfsr_next(8) = lfsr(7);
        lfsr_next(9) = lfsr(8);        
        lfsr_next(10)= xor(common, lfsr(9));
        lfsr_next(11)= xor(common, lfsr(10));
        lfsr_next(12) = lfsr(11);  
        lfsr_next(13) = lfsr(12);  
        lfsr_next(14) = lfsr(13);  
        lfsr_next(15) = lfsr(14);  
        lfsr_next(16) = lfsr(15);  
        lfsr_next(17) = lfsr(16);  
        lfsr_next(18) = lfsr(17);  
        lfsr_next(19) = lfsr(18);  
        lfsr_next(20) = lfsr(19);  
        lfsr_next(21) = lfsr(20);  
        lfsr_next(22) = lfsr(21);  
        lfsr_next(23) = lfsr(22);  
        lfsr_next(24) = lfsr(23);                       % position 23
        
        lfsr=lfsr_next;
    end
    
%     lfsr_dec=0;
%     for jj=1:numel(lfsr)
%        lfsr_dec=lfsr_dec+lfsr(jj)*2^(jj-1);
%     end
    
%     lfsr_dec=dec2hex(lfsr_dec);
end
