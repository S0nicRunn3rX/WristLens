"""Original short UI cues. Deterministic PCM; no external samples or runtime synthesis."""
from pathlib import Path
import math, random, struct, wave

RATE=24000
DEST=Path(__file__).resolve().parents[1]/'wear/src/main/res/raw'
DEST.mkdir(exist_ok=True,parents=True)
def write(name,samples):
    with wave.open(str(DEST/(name+'.wav')),'wb') as f:
        f.setnchannels(1);f.setsampwidth(2);f.setframerate(RATE)
        f.writeframes(b''.join(struct.pack('<h',round(max(-1,min(1,x))*32767)) for x in samples))
def tone(freq,seconds):
    n=round(RATE*seconds)
    return [.36*math.sin(2*math.pi*freq*i/RATE)*math.sin(math.pi*i/n)**2 for i in range(n)]
def notes(freqs):
    result=[]
    for freq in freqs:result+=tone(freq,.065)+[0.]*(RATE//100)
    return result
def click(burst):
    rng=random.Random(1600+(1 if burst else 0));duration=.065 if burst else .105
    out=[];previous=0
    for i in range(round(RATE*duration)):
        t=i/RATE;noise=rng.uniform(-1,1);high=noise-previous;previous=noise
        env=math.exp(-t/0.009)+(0.6*math.exp(-(t-.035)/.012) if t>=.035 and not burst else 0)
        out.append(.28*high*env+.10*math.sin(2*math.pi*1800*t)*math.exp(-t/.018))
    return out
write('shutter',click(False));write('burst_shutter',click(True))
for name,freqs in [('record_start',[660,990]),('record_pause',[440]),('record_resume',[880]),('record_stop',[660,440])]:write(name,notes(freqs))
