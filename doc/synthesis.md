# Synthesis Results

Sweep xyWidth, set zWidth = 12, stagesPerCycle = 1:
![xywidth](https://user-images.githubusercontent.com/18479261/46647786-c77ff080-cb46-11e8-9307-58d7ae28fb96.png)


Sweep zWidth, set xyWidth = 12, stagesPerCycle = 1:
![zwidth](https://user-images.githubusercontent.com/18479261/46647787-cbac0e00-cb46-11e8-98f5-977f4fdf2233.png)



As both width increases the number of LUT increases in a roughly linear fashion, but for xy the increase in LUT numbers is larger than that in z for the same width increase (roughly 10 times more)

Sweep stagesPerCycle. set xyWidth = 15, zWidth = 15:

(Because of the restriction that nStages must be multiple of stagesPerCycle, only three data points at 1,2 and 7)

![stagespercycle](https://user-images.githubusercontent.com/18479261/46647789-cd75d180-cb46-11e8-89a8-bc8d42617782.png)

As the degree of loop unrolling increases the number of LUT increases drastically.


