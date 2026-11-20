import json,sys
def lum(h):
    h=h.lstrip('#');c=[int(h[i:i+2],16)/255 for i in (0,2,4)]
    c=[x/12.92 if x<=0.04045 else ((x+0.055)/1.055)**2.4 for x in c]
    return 0.2126*c[0]+0.7152*c[1]+0.0722*c[2]
def cr(a,b):
    la,lb=lum(a),lum(b);return (max(la,lb)+0.05)/(min(la,lb)+0.05)
P={
 'dark':dict(bg='#111318',surface='#171A21',subtle='#1F232C',textPrimary='#E9E7E2',textSecondary='#A6ABB5',border='#272B35',controlBorder='#6C7382',sun='#D6C07E',moon='#AFBDE3',blueHour='#7F93D0',skyDay='#272C38',skyCivil='#212532',skyNautical='#1B1F29',skyAstro='#161920',success='#8DB38F',danger='#E39A93',focusRing='#AFBDE3'),
 'light':dict(bg='#FAF9F6',surface='#F3F1EC',subtle='#ECE9E2',textPrimary='#2C2C2C',textSecondary='#5C5A54',border='#E6E3DC',controlBorder='#857F74',sun='#7A6220',moon='#3D5896',blueHour='#4F67A6',skyDay='#FAF9F6',skyCivil='#EEEBE4',skyNautical='#E5E1D8',skyAstro='#DCD7CB',success='#4A784E',danger='#A2443C',focusRing='#3D5896'),
}
pairs=[('textPrimary','bg','text'),('textPrimary','surface','text'),('textPrimary','subtle','text'),('textSecondary','bg','text'),('textSecondary','surface','text'),('textSecondary','subtle','text'),('bg','textPrimary','text'),('sun','bg','text'),('sun','surface','text'),('moon','bg','text'),('moon','surface','text'),('danger','bg','text'),('danger','surface','text'),('success','bg','text'),
('textSecondary','skyDay','text'),('textSecondary','skyCivil','text'),('textSecondary','skyNautical','text'),('textSecondary','skyAstro','text'),('textSecondary','bg','text'),
('controlBorder','bg','boundary'),('controlBorder','surface','boundary'),('controlBorder','subtle','boundary'),('focusRing','bg','boundary'),('focusRing','surface','boundary'),('sun','subtle','boundary'),
('sun','skyDay','graphic'),('sun','skyCivil','graphic'),('sun','skyNautical','graphic'),('sun','skyAstro','graphic'),('moon','skyDay','graphic'),('moon','skyAstro','graphic'),('moon','bg','graphic'),('blueHour','surface','graphic'),('blueHour','bg','graphic'),('textPrimary','skyDay','graphic'),('textPrimary','skyAstro','graphic'),('sun','bg','graphic'),('moon','surface','graphic'),('textPrimary','skyDay','text'),('textSecondary','skyCivil','graphic'),('bg','sun','text')]
rows=[];mt=99;mg=99;ok=True
for th,p in P.items():
  for f,b,k in pairs:
    r=cr(p[f],p[b]);need=4.5 if k=='text' else 3.0;res=r>=need;ok&=res
    if k=='text':mt=min(mt,r)
    else:mg=min(mg,r)
    rows.append((th,f,p[f],b,p[b],k,r,res))
for r in rows:
  if not r[7] or '-v' in sys.argv: print('%s %s %s on %s %s %s %.2f %s'%(r[0],r[1],r[2],r[3],r[4],r[5],r[6],'pass' if r[7] else 'FAIL'))
print('min text %.2f, min graphic/boundary %.2f, all pass %s'%(mt,mg,ok))
json.dump([list(r) for r in rows],open(sys.argv[1] if len(sys.argv)>1 and sys.argv[1]!='-v' else '/dev/null','w'))
