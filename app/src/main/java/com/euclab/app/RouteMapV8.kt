package com.euclab.app

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.euclab.app.data.RideLog
import kotlin.math.absoluteValue

/**
 * Self-contained route renderer.
 *
 * v0.0.13 fix: the WebView is no longer reloaded on every Compose recomposition.
 * Active telemetry used to trigger repeated loadDataWithBaseURL() calls before the
 * route JavaScript could finish rendering, which could leave the card blank.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun V8RouteMap(ride: RideLog, colorByPwm: Boolean, modifier: Modifier = Modifier) {
    val points = remember(ride.file.absolutePath, colorByPwm) {
        ride.gpsSamples
            .filter { it.latitude != null && it.longitude != null }
            .let { source ->
                if (source.size <= 900) source
                else {
                    val step = (source.size / 900).coerceAtLeast(1)
                    source.filterIndexed { index, _ -> index % step == 0 }
                }
            }
    }
    val html = remember(points, colorByPwm) { buildV8MapHtml(points.map { p ->
        Triple(p.latitude!!, p.longitude!!, if (colorByPwm) p.pwmPercent else p.speedKmh.absoluteValue)
    }, colorByPwm) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                setBackgroundColor(AndroidColor.rgb(10, 13, 18))
                // Some vendor WebViews are more reliable for SVG overlays in software mode.
                setLayerType(WebView.LAYER_TYPE_SOFTWARE, null)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript("if (typeof render === 'function') { render(); setTimeout(render, 180); }", null)
                    }
                }
                tag = html
                loadDataWithBaseURL("https://euc-lab.local/", html, "text/html", "UTF-8", null)
            }
        },
        update = { web ->
            // Critical: do not reload the WebView just because Compose recomposed.
            if (web.tag != html) {
                web.tag = html
                web.loadDataWithBaseURL("https://euc-lab.local/", html, "text/html", "UTF-8", null)
            } else {
                web.evaluateJavascript("if (typeof render === 'function') render();", null)
            }
        },
    )
}

private fun buildV8MapHtml(points: List<Triple<Double, Double, Float>>, pwm: Boolean): String {
    val jsPoints = points.joinToString(",") { (lat, lon, value) ->
        "{lat:${"%.7f".format(java.util.Locale.US, lat)},lon:${"%.7f".format(java.util.Locale.US, lon)},v:${"%.3f".format(java.util.Locale.US, value)}}"
    }
    val mode = if (pwm) "PWM" else "SPEED"
    return """
<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no" />
<style>
html,body,#map{width:100%;height:100%;margin:0;overflow:hidden;background:#0a0d12;font-family:system-ui,sans-serif}
#tiles{position:absolute;inset:0;overflow:hidden;background:linear-gradient(145deg,#0c1117,#10161e)}
.tile{position:absolute;width:256px;height:256px;filter:grayscale(1) invert(.91) sepia(.10) hue-rotate(175deg) brightness(.48) contrast(1.18);opacity:.78}
#shade{position:absolute;inset:0;background:rgba(8,11,15,.18);pointer-events:none}
#route{position:absolute;inset:0;width:100%;height:100%;pointer-events:none}
.badge{position:absolute;left:10px;top:10px;background:rgba(9,12,17,.86);color:#cad2dd;border:1px solid rgba(255,255,255,.10);border-radius:10px;padding:6px 8px;font-size:10px;font-weight:700;letter-spacing:.4px}
.attr{position:absolute;right:5px;bottom:4px;background:rgba(9,12,17,.70);color:#7c8798;border-radius:5px;padding:2px 4px;font-size:7px}
.empty{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;color:#7c8798;font-size:12px}
</style>
</head>
<body>
<div id="map"><div id="tiles"></div><div id="shade"></div><svg id="route"></svg><div class="badge">$mode · ${points.size} GPS</div><div class="attr">© OpenStreetMap</div></div>
<script>
const pts=[$jsPoints];
const map=document.getElementById('map'), tiles=document.getElementById('tiles'), svg=document.getElementById('route');
const TS=256;
function merc(lat,lon,z){
  const n=Math.pow(2,z), x=(lon+180)/360*n*TS;
  const rad=Math.max(-85.05112878,Math.min(85.05112878,lat))*Math.PI/180;
  const y=(1-Math.log(Math.tan(rad)+1/Math.cos(rad))/Math.PI)/2*n*TS;
  return {x,y};
}
function clr(v){
  const hue=${if (pwm) "120-Math.max(0,Math.min(100,v))*1.2" else "210-Math.max(0,Math.min(80,v))*2.1"};
  return `hsl(${ '$' }{Math.round(hue)},92%,57%)`;
}
function render(){
  if(!pts.length){ tiles.innerHTML='<div class="empty">No GPS points</div>'; return; }
  const w=map.clientWidth||600,h=map.clientHeight||300,pad=28;
  let z=18,b=null;
  for(;z>=3;z--){
    const pp=pts.map(p=>merc(p.lat,p.lon,z));
    const xs=pp.map(p=>p.x),ys=pp.map(p=>p.y);
    const bb={minx:Math.min(...xs),maxx:Math.max(...xs),miny:Math.min(...ys),maxy:Math.max(...ys),pp};
    if((bb.maxx-bb.minx)<=w-pad*2 && (bb.maxy-bb.miny)<=h-pad*2){b=bb;break;}
  }
  if(!b){const pp=pts.map(p=>merc(p.lat,p.lon,z));const xs=pp.map(p=>p.x),ys=pp.map(p=>p.y);b={minx:Math.min(...xs),maxx:Math.max(...xs),miny:Math.min(...ys),maxy:Math.max(...ys),pp};}
  const cx=(b.minx+b.maxx)/2,cy=(b.miny+b.maxy)/2,left=cx-w/2,top=cy-h/2;
  tiles.innerHTML='';
  const n=Math.pow(2,z);
  const sx=Math.floor(left/TS)-1,ex=Math.floor((left+w)/TS)+1,sy=Math.floor(top/TS)-1,ey=Math.floor((top+h)/TS)+1;
  for(let ty=sy;ty<=ey;ty++) for(let tx=sx;tx<=ex;tx++){
    if(ty<0||ty>=n) continue;
    const wrap=((tx%n)+n)%n;
    const img=document.createElement('img'); img.className='tile'; img.loading='eager'; img.decoding='async';
    img.src=`https://tile.openstreetmap.org/${ '$' }{z}/${ '$' }{wrap}/${ '$' }{ty}.png`;
    img.style.left=(tx*TS-left)+'px'; img.style.top=(ty*TS-top)+'px'; tiles.appendChild(img);
  }
  svg.setAttribute('viewBox',`0 0 ${ '$' }{w} ${ '$' }{h}`); svg.innerHTML='';
  const ns='http://www.w3.org/2000/svg';
  for(let i=1;i<b.pp.length;i++){
    const a=b.pp[i-1],q=b.pp[i],line=document.createElementNS(ns,'line');
    line.setAttribute('x1',a.x-left);line.setAttribute('y1',a.y-top);line.setAttribute('x2',q.x-left);line.setAttribute('y2',q.y-top);
    line.setAttribute('stroke',clr(pts[i].v));line.setAttribute('stroke-width','5');line.setAttribute('stroke-linecap','round');line.setAttribute('opacity','.96');svg.appendChild(line);
  }
  [[0,'#6dff9a'],[b.pp.length-1,'#ff5060']].forEach(([idx,c])=>{const p=b.pp[idx],circle=document.createElementNS(ns,'circle');circle.setAttribute('cx',p.x-left);circle.setAttribute('cy',p.y-top);circle.setAttribute('r','6');circle.setAttribute('fill',c);circle.setAttribute('stroke','#0a0d12');circle.setAttribute('stroke-width','3');svg.appendChild(circle);});
}
window.addEventListener('resize',render);
window.addEventListener('load',()=>{render();setTimeout(render,120);setTimeout(render,500);});
setTimeout(render,20);
</script>
</body>
</html>
""".trimIndent()
}
