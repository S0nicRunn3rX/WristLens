#!/usr/bin/env python3
"""Run exact GlPreview GLSL on EGL ES2 with Android text textures after phone tests.
Validates compositing and rotation, not Android HAL or MediaRecorder. Requires Pillow/Mesa.
Run from the project root: python3 tools/test_gpu_watermark.py
"""
import ctypes as C, ctypes.util, json, re
from pathlib import Path
from PIL import Image
V=C.c_void_p;I=C.c_int;U=C.c_uint;F=C.c_float;B=C.c_ubyte
EGL=C.CDLL(ctypes.util.find_library('EGL'));EGL.eglGetProcAddress.argtypes=[C.c_char_p];EGL.eglGetProcAddress.restype=V
keep={}
def fun(name,result,*args):
 addr=EGL.eglGetProcAddress(name.encode());assert addr,name;keep[name]=C.CFUNCTYPE(result,*args)(addr);return keep[name]
def ints(*a):return (I*len(a))(*a)
def floats(a):return (F*len(a))(*a)
display=fun('eglGetPlatformDisplayEXT',V,U,V,C.POINTER(I))(0x31DD,None,None);assert display
assert fun('eglInitialize',U,V,C.POINTER(I),C.POINTER(I))(display,C.byref(I()),C.byref(I()))
assert fun('eglBindAPI',U,U)(0x30A0)
config=V();count=I()
assert fun('eglChooseConfig',U,V,C.POINTER(I),C.POINTER(V),I,C.POINTER(I))(display,ints(0x3033,1,0x3040,4,0x3024,8,0x3023,8,0x3022,8,0x3021,8,0x3038),C.byref(config),1,C.byref(count)) and count.value
context=fun('eglCreateContext',V,V,V,V,C.POINTER(I))(display,config,None,ints(0x3098,2,0x3038));assert context
surface=fun('eglCreatePbufferSurface',V,V,V,C.POINTER(I))(display,config,ints(0x3057,640,0x3056,480,0x3038));assert surface
assert fun('eglMakeCurrent',U,V,V,V,V)(display,surface,surface,context)
renderer=fun('glGetString',C.c_char_p,U)(0x1F01).decode();print('Renderer:',renderer)
active=fun('glActiveTexture',None,U);bind=fun('glBindTexture',None,U,U);param=fun('glTexParameteri',None,U,U,I)
gen=fun('glGenTextures',None,I,C.POINTER(U));teximage=fun('glTexImage2D',None,U,I,I,I,I,I,U,U,V)
def texture(target):
 t=U();gen(1,C.byref(t));bind(target,t.value)
 for key,value in [(0x2801,0x2601),(0x2800,0x2601),(0x2802,0x812F),(0x2803,0x812F)]:param(target,key,value)
 return t.value
active(0x84C0);camera=texture(0x0DE1);base=bytes([112,128,144,255])*(640*480);raw=C.create_string_buffer(base);teximage(0x0DE1,0,0x1908,640,480,0,0x1908,0x1401,raw)
image=fun('eglCreateImageKHR',V,V,V,U,V,C.POINTER(I))(display,context,0x30B1,V(camera),ints(0x3038));assert image
external=texture(0x8D65);fun('glEGLImageTargetTexture2DOES',None,U,V)(0x8D65,image)
active(0x84C1);stamp=texture(0x0DE1)
source=Path('phone/src/main/java/com/daniil/wristlens/phone/GlPreview.java').read_text();match=re.search(r'program=program\(("(?:[^"\\]|\\.)*"),("(?:[^"\\]|\\.)*")\)',source);assert match
create=fun('glCreateShader',U,U);shaderSource=fun('glShaderSource',None,U,I,C.POINTER(C.c_char_p),C.POINTER(I));compileShader=fun('glCompileShader',None,U);getShader=fun('glGetShaderiv',None,U,U,C.POINTER(I));shaderLog=fun('glGetShaderInfoLog',None,U,I,C.POINTER(I),V)
def shader(kind,text):
 s=create(kind);encoded=C.c_char_p(text.encode());shaderSource(s,1,C.byref(encoded),None);compileShader(s);ok=I();getShader(s,0x8B81,C.byref(ok));log=C.create_string_buffer(8192);shaderLog(s,8192,None,log);assert ok.value,log.value;return s
vs=shader(0x8B31,json.loads(match.group(1)));fs=shader(0x8B30,json.loads(match.group(2)));program=fun('glCreateProgram',U)();attach=fun('glAttachShader',None,U,U);attach(program,vs);attach(program,fs);fun('glLinkProgram',None,U)(program);ok=I();fun('glGetProgramiv',None,U,U,C.POINTER(I))(program,0x8B82,C.byref(ok));assert ok.value
fun('glUseProgram',None,U)(program);loc=fun('glGetUniformLocation',I,U,C.c_char_p);u1i=fun('glUniform1i',None,I,I);u1f=fun('glUniform1f',None,I,F);u2f=fun('glUniform2f',None,I,F,F)
def location(name):return loc(program,name.encode())
u1i(location('image'),0);u1i(location('stamp'),1);identity=floats([1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]);fun('glUniformMatrix4fv',None,I,I,B,C.POINTER(F))(location('m'),1,0,identity)
quad=floats([-1,-1,1,-1,-1,1,1,1]);at=fun('glGetAttribLocation',I,U,C.c_char_p)(program,b'p');fun('glEnableVertexAttribArray',None,U)(at);fun('glVertexAttribPointer',None,U,I,U,B,I,V)(at,2,0x1406,0,0,quad);fun('glViewport',None,I,I,I,I)(0,0,640,480)
draw=fun('glDrawArrays',None,U,I,I);read=fun('glReadPixels',None,I,I,I,I,U,U,V);error=fun('glGetError',U)
folder=Path('phone/build/gpu-watermark-input');cases=json.loads((folder/'cases.json').read_text());out=Path('phone/build/gpu-watermark-output');out.mkdir(exist_ok=True);results=[]
for case in cases:
 bitmap=Image.open(folder/(case['name']+'.png')).convert('RGBA');pixels=bytearray(bitmap.tobytes())
 for pos in range(0,len(pixels),4):
  a=pixels[pos+3]
  for c in range(3):pixels[pos+c]=(pixels[pos+c]*a+127)//255
 raw=C.create_string_buffer(bytes(pixels));teximage(0x0DE1,0,0x1908,bitmap.width,bitmap.height,0,0x1908,0x1401,raw)
 v=case['vertices'];u2f(location('origin'),(v[0]+1)/2,(v[1]+1)/2);u2f(location('edgeU'),(v[2]-v[0])/2,(v[3]-v[1])/2);u2f(location('edgeV'),(v[4]-v[0])/2,(v[5]-v[1])/2);u1f(location('marked'),1);draw(5,0,4);buf=(B*(640*480*4))();read(0,0,640,480,0x1908,0x1401,buf);assert error()==0
 result=Image.frombytes('RGBA',(640,480),bytes(buf)).transpose(Image.Transpose.FLIP_TOP_BOTTOM).rotate(-case['rotation'],expand=True);mask=result.getchannel('R').point(lambda x:255 if (x>220 if case['color']=='white' else x<30) else 0);box=mask.getbbox();assert box,case;n=sum(mask.histogram()[1:]);assert n>25,(case,n);left,top,right,bottom=box;assert (left+right>result.width)==case['corner'].endswith('right'),(case,box);assert (top+bottom>result.height)==case['corner'].startswith('bottom'),(case,box)
 result.save(out/(case['name']+'.png'));results.append({'case':case['name'],'pixels':n,'bounds':box})
u1f(location('marked'),0);draw(5,0,4);buf=(B*(640*480*4))();read(0,0,640,480,0x1908,0x1401,buf);assert bytes(buf)==base,'Unmarked preview changed'
report={'renderer':renderer,'passed':len(results),'preview_unmarked':True,'scope':'Real External OES shader with Android text; no phone HAL or MediaRecorder validation','cases':results};(out/'report.json').write_text(json.dumps(report,indent=2));print('PASS:',len(results),'watermark cases + unmarked preview')
