#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

export HAMMER_HOME=$DIR/hammer

source $HAMMER_HOME/sourceme.sh

cd "$DIR" && hammer-vlsi synthesis -o syn-output.json -v IterativeCordic.v --top IterativeCordic -p config/clocks.json -p config/use_vivado.json  --obj_dir out
