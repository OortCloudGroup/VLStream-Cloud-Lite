package com.ruoyi.custom.availability;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Conservative bounded media structure checks. Evidence of encoded samples, never a decoder guarantee. */
final class CustomVideoContent {
    private CustomVideoContent() { }
    static int number(byte[] b,int at){return CustomHttpStreamProbe.integer(b,at);}
    static class Box {
        final int start,data,end;final String type;
        Box(int start,int data,int end,String type){this.start=start;this.data=data;this.end=end;this.type=type;}
    }
    static List<Box> boxes(byte[] b,int start,int end){
        List<Box> result=new ArrayList<>();
        for(int at=start;at+8<=end;){long size=number(b,at)&0xffffffffL;int header=8;
            if(size==1){if(at+16>end)return Collections.emptyList();long high=number(b,at+8)&0xffffffffL;if(high!=0)return Collections.emptyList();size=number(b,at+12)&0xffffffffL;header=16;}
            if(size==0)size=end-at;
            if(size<header)return result;
            if(size>end-at){if("mdat".equals(new String(b,at+4,4,StandardCharsets.US_ASCII)))result.add(new Box(at,at+header,end,"mdat"));return result;}
            result.add(new Box(at,at+header,(int)(at+size),new String(b,at+4,4,StandardCharsets.US_ASCII)));at+=size;
            if(result.size()>4096)return Collections.emptyList();
        }return result;
    }
    static Box find(byte[] b,Box parent,String type){for(Box box:boxes(b,parent.data,parent.end))if(type.equals(box.type))return box;return null;}
    static Box root(byte[] b,String type){for(Box box:boxes(b,0,b.length))if(type.equals(box.type))return box;return null;}
    static Box videoTrack(byte[] b){Box moov=root(b,"moov");if(moov==null)return null;
        for(Box track:boxes(b,moov.data,moov.end)){if(!"trak".equals(track.type))continue;Box mdia=find(b,track,"mdia");if(mdia==null)continue;Box handler=find(b,mdia,"hdlr");
            if(handler==null||handler.end-handler.data<20||!"vide".equals(new String(b,handler.data+8,4,StandardCharsets.US_ASCII)))continue;
            Box minf=find(b,mdia,"minf"),stbl=minf==null?null:find(b,minf,"stbl"),stsd=stbl==null?null:find(b,stbl,"stsd");
            if(stsd==null||stsd.end-stsd.data<8||number(b,stsd.data+4)<1)continue;
            for(Box sample:boxes(b,stsd.data+8,stsd.end))if(Arrays.asList("avc1","avc3","hvc1","hev1").contains(sample.type)&&sample.end-sample.data>=78){
                if(sampleCodec(b,sample)>0)return track;
            }
        }return null;
    }
    static int sampleCodec(byte[] b,Box sample){int codec=sample.type.startsWith("avc")?7:12;
        for(Box config:boxes(b,sample.data+78,sample.end))if((codec==7?"avcC":"hvcC").equals(config.type)){
            byte[] packet=new byte[5+config.end-config.data];packet[0]=(byte)(0x10|codec);System.arraycopy(b,config.data,packet,5,config.end-config.data);
            if(CustomRtmpStreamProbe.videoConfig(packet))return codec;
        }return 0;
    }
    static int trackCodec(byte[] b,Box track){Box mdia=find(b,track,"mdia"),minf=find(b,mdia,"minf"),stbl=find(b,minf,"stbl"),stsd=find(b,stbl,"stsd");
        for(Box sample:boxes(b,stsd.data+8,stsd.end))if(sample.end-sample.data>=78&&Arrays.asList("avc1","avc3","hvc1","hev1").contains(sample.type)){int codec=sampleCodec(b,sample);if(codec>0)return codec;}return 0;
    }
    static boolean codedSample(byte[] b,int offset,int length,int codec){
        if(length<6||offset<0||offset+(long)length>b.length)return false;int nal=number(b,offset);
        if(nal<2||nal>length-4)return false;int header=b[offset+4]&255;
        return (header&128)==0&&(codec==7?(header&31)>=1&&(header&31)<=5:((header>>1)&63)<=21&&(b[offset+5]&7)!=0);
    }
    static boolean mp4(byte[] b){Box track=videoTrack(b),mdat=root(b,"mdat");if(track==null||mdat==null)return false;
        Box mdia=find(b,track,"mdia"),minf=find(b,mdia,"minf"),stbl=find(b,minf,"stbl"),sizes=find(b,stbl,"stsz"),offsets=find(b,stbl,"stco");
        if(sizes==null||offsets==null||sizes.end-sizes.data<12||offsets.end-offsets.data<12)return false;
        int count=number(b,sizes.data+8);if(count<=0||count>1000000||number(b,offsets.data+4)<=0)return false;
        int size=number(b,sizes.data+4);if(size==0){if(sizes.end-sizes.data<16)return false;size=number(b,sizes.data+12);}
        int offset=number(b,offsets.data+8);return offset>=mdat.data&&offset+(long)size<=mdat.end&&codedSample(b,offset,size,trackCodec(b,track));
    }
    static boolean fragmented(byte[] init,byte[] segment){Box track=videoTrack(init),moof=root(segment,"moof"),mdat=root(segment,"mdat");if(track==null||moof==null||mdat==null)return false;
        Box tkhd=find(init,track,"tkhd");if(tkhd==null||tkhd.end-tkhd.data<24)return false;int id=number(init,tkhd.data+((init[tkhd.data]&255)==1?20:12));
        for(Box traf:boxes(segment,moof.data,moof.end)){if(!"traf".equals(traf.type))continue;Box tfhd=find(segment,traf,"tfhd"),trun=find(segment,traf,"trun");
            if(tfhd==null||trun==null||tfhd.end-tfhd.data<8||trun.end-trun.data<8||number(segment,tfhd.data+4)!=id||(number(segment,tfhd.data)&0x020000)==0)continue;
            int flags=number(segment,trun.data)&0xffffff,count=number(segment,trun.data+4);if(count<=0||count>1000000||(flags&0x200)==0||(flags&1)==0)continue;
            int sample=trun.data+12+((flags&4)!=0?4:0)+((flags&0x100)!=0?4:0);if(sample+4>trun.end)continue;
            int size=number(segment,sample),offset=number(segment,trun.data+8)+moof.start;
            if(offset>=mdat.data&&offset+(long)size<=mdat.end&&codedSample(segment,offset,size,trackCodec(init,track)))return true;
        }return false;
    }
    static boolean ts(byte[] b){
        return tsState(b)==2;
    }
    static int tsState(byte[] b){
        int pmt=-1,video=-1,codec=0;Set<Integer> videoTypes=new HashSet<>(Arrays.asList(0x1b,0x24,0x02));
        for(int at=0;at+188<=b.length;at+=188){if((b[at]&255)!=0x47||(b[at+1]&0x80)!=0)return -1;
            int pid=((b[at+1]&31)<<8)|(b[at+2]&255),control=(b[at+3]>>4)&3;if(control==0||control==2)continue;
            int payload=at+4+(control==3?1+(b[at+4]&255):0);if(payload>=at+188)continue;
            boolean start=(b[at+1]&0x40)!=0;
            if(start&&(pid==0||pid==pmt)){int section=payload+1+(b[payload]&255);if(section+12>at+188)continue;int length=((b[section+1]&15)<<8)|(b[section+2]&255),end=section+3+length-4;
                if(length<9||end>at+188)continue;
                if(pid==0&&b[section]==0){for(int i=section+8;i+4<=end;i+=4)if(b[i]!=0||b[i+1]!=0){pmt=((b[i+2]&31)<<8)|(b[i+3]&255);break;}}
                else if(pid==pmt&&b[section]==2){int info=((b[section+10]&15)<<8)|(b[section+11]&255);for(int i=section+12+info;i+5<=end;){int size=((b[i+3]&15)<<8)|(b[i+4]&255);if(videoTypes.contains(b[i]&255)){video=((b[i+1]&31)<<8)|(b[i+2]&255);codec=b[i]&255;}i+=5+size;}}
            }
            if(pid==video&&start&&payload+10<at+188&&b[payload]==0&&b[payload+1]==0&&b[payload+2]==1&&(b[payload+3]&255)>=0xe0&&(b[payload+3]&255)<=0xef){
                int data=payload+9+(b[payload+8]&255);
                for(int i=data;i+5<at+188;i++)if(b[i]==0&&b[i+1]==0&&b[i+2]==1){int code=b[i+3]&255;if((code&128)==0&&(codec==0x1b&&(code&31)>=1&&(code&31)<=5||codec==0x24&&(code>>1&63)<=21&&(b[i+4]&7)!=0)||codec==2&&code>=1&&code<=0xaf)return 2;}
            }
        }return video>=0?1:0;
    }
}
