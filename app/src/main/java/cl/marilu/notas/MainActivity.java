package cl.marilu.notas;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.view.*;
import android.widget.*;
import java.io.*;
import org.json.*;

/** Initial offline prototype. No account or network permission required. */
public class MainActivity extends Activity {
    LinearLayout root;
    EditText title, text;
    Ink ink;
    File current;
    PdfRenderer pdf;
    int page;
    JSONObject document;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(state!=null && state.containsKey("note")) {
            open(new File(getFilesDir(),state.getString("note")));
            page=state.getInt("page",0);
        } else library();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        save();
        if(current!=null){state.putString("note",current.getName());state.putInt("page",page);}
        super.onSaveInstanceState(state);
    }
    void message(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    Button button(String label, Runnable action) {
        Button b=new Button(this); b.setText(label); b.setOnClickListener(v->action.run()); return b;
    }
    void layout() {
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16,16,16,16); root.setBackgroundColor(Color.rgb(246,244,240)); setContentView(root);
    }
    void library() {
        closePdf(); current=null; ink=null; layout();
        TextView heading=new TextView(this); heading.setText("Mis notas"); heading.setTextSize(28); root.addView(heading);
        root.addView(button("+ Nueva nota",()->open(new File(getFilesDir(),System.currentTimeMillis()+".json"))));
        ScrollView scroll=new ScrollView(this); LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list); root.addView(scroll);
        File[] files=getFilesDir().listFiles((d,n)->n.endsWith(".json"));
        if(files!=null) { java.util.Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
            for(File f:files) try { JSONObject j=read(f); list.addView(button(j.optString("title","Sin título"),()->open(f))); }
            catch(Exception e) { list.addView(button("Nota que requiere revisión",()->message("No se pudo leer "+f.getName()))); }
        }
    }
    JSONObject read(File f) throws Exception {
        if(!f.exists()) return new JSONObject();
        try(FileInputStream in=new FileInputStream(f); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int n; while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            return new JSONObject(out.toString("UTF-8"));
        }
    }
    void open(File f) {
        try { document=read(f); } catch(Exception e) { message("No se pudo abrir la nota"); return; }
        current=f; page=0; layout();
        title=new EditText(this); title.setSingleLine(true); title.setHint("Título de la nota"); title.setText(document.optString("title","")); root.addView(title);
        LinearLayout row=new LinearLayout(this); root.addView(row);
        row.addView(button("Notas",()->{if(save())library();}));
        row.addView(button("PDF",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("application/pdf"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,10);}));
        row.addView(button("Guardar",()->{if(save())message("Nota guardada");}));
        text=new EditText(this); text.setHint("Escribe aquí con el teclado…"); text.setText(document.optString("text","")); text.setMinLines(2); text.setMaxLines(4); text.setGravity(Gravity.TOP); root.addView(text);
        HorizontalScrollView tools=new HorizontalScrollView(this); LinearLayout toolbar=new LinearLayout(this); tools.addView(toolbar); root.addView(tools);
        ink=new Ink();
        toolbar.addView(button("Lápiz",()->{ink.eraser=false; ink.color=Color.rgb(36,42,53);}));
        toolbar.addView(button("Azul",()->{ink.eraser=false; ink.color=Color.rgb(40,92,185);}));
        toolbar.addView(button("Resaltar",()->{ink.eraser=false; ink.color=0x66FFD54F;}));
        toolbar.addView(button("Borrador",()->ink.eraser=true));
        toolbar.addView(button("Deshacer",()->ink.undo()));
        toolbar.addView(button("‹ Página",()->changePage(-1)));
        toolbar.addView(button("Página ›",()->changePage(1)));
        root.addView(ink,new LinearLayout.LayoutParams(-1,0,1));
        String path=document.optString("pdf","");
        if(!path.isEmpty()) try { pdf=new PdfRenderer(ParcelFileDescriptor.open(new File(getFilesDir(),path),ParcelFileDescriptor.MODE_READ_ONLY)); }
        catch(Exception e) { message("No se pudo cargar el PDF"); }
        ink.post(()->{ink.load(); render();});
    }
    void changePage(int delta) {
        if(pdf==null)return; int next=page+delta; if(next<0||next>=pdf.getPageCount())return;
        if(!save())return; page=next; ink.load(); render(); message("Página "+(page+1)+" de "+pdf.getPageCount());
    }
    void render() {
        if(ink.background!=null){ink.background.recycle();ink.background=null;}
        if(pdf!=null) try(PdfRenderer.Page p=pdf.openPage(page)) {
            int w=Math.min(1600,Math.max(600,ink.getWidth())); int h=Math.round(w*(float)p.getHeight()/p.getWidth());
            ink.background=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); ink.background.eraseColor(Color.WHITE);
            p.render(ink.background,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        } catch(Exception e){message("No se pudo mostrar la página");}
        ink.invalidate();
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data); if(request!=10||result!=RESULT_OK||data==null)return;
        if(pdf!=null) {message("Crea una nota nueva para importar otro PDF");return;}
        File dest=new File(getFilesDir(),current.getName()+".pdf");
        try(InputStream in=getContentResolver().openInputStream(data.getData()); FileOutputStream out=new FileOutputStream(dest)) {
            if(in==null)throw new IOException(); byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
        } catch(Exception e){dest.delete();message("No se pudo importar el PDF");return;}
        try { pdf=new PdfRenderer(ParcelFileDescriptor.open(dest,ParcelFileDescriptor.MODE_READ_ONLY)); document.put("pdf",dest.getName()); page=0; save(); render(); }
        catch(Exception e){closePdf();dest.delete();message("PDF no compatible o protegido con contraseña");}
    }
    boolean save() {
        if(current==null||ink==null)return true;
        android.util.AtomicFile file=new android.util.AtomicFile(current); FileOutputStream out=null;
        try {
            ink.commit(); document.put("title",title.getText().toString().trim().isEmpty()?"Sin título":title.getText().toString());
            document.put("text",text.getText().toString()); JSONObject pages=document.optJSONObject("pages");if(pages==null){pages=new JSONObject();document.put("pages",pages);} pages.put(""+page,ink.strokes);
            out=file.startWrite();out.write(document.toString().getBytes("UTF-8"));file.finishWrite(out);return true;
        } catch(Exception e){if(out!=null)file.failWrite(out);message("No se pudo guardar la nota");return false;}
    }
    void closePdf(){if(pdf!=null){pdf.close();pdf=null;}}
    @Override protected void onPause(){save();super.onPause();}
    @Override protected void onDestroy(){closePdf();super.onDestroy();}
    @Override public void onBackPressed(){if(current!=null){if(save())library();}else super.onBackPressed();}

    class Ink extends View {
        JSONArray strokes=new JSONArray(), points;
        JSONObject active;
        Bitmap background;
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        int color=Color.rgb(36,42,53), pointer=-1;
        boolean eraser=false;
        Ink(){super(MainActivity.this);setBackgroundColor(Color.WHITE);}
        void load(){JSONObject pages=document.optJSONObject("pages");strokes=pages==null?null:pages.optJSONArray(""+page);if(strokes==null)strokes=new JSONArray();active=null;pointer=-1;invalidate();}
        void commit(){if(active!=null){strokes.put(active);active=null;pointer=-1;}invalidate();}
        void undo(){commit();if(strokes.length()>0)strokes.remove(strokes.length()-1);save();invalidate();}
        RectF bounds(){
            if(background==null)return new RectF(0,0,getWidth(),getHeight());
            float scale=Math.min(getWidth()/(float)background.getWidth(),getHeight()/(float)background.getHeight());
            float w=background.getWidth()*scale,h=background.getHeight()*scale;
            return new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);
        }
        @Override protected void onDraw(Canvas c){
            RectF b=bounds(); if(background!=null)c.drawBitmap(background,null,b,null);
            else{paint.setColor(0xFFE5E9EF);paint.setStrokeWidth(1);for(int y=40;y<getHeight();y+=40)c.drawLine(0,y,getWidth(),y,paint);}
            c.save();c.clipRect(b);for(int i=0;i<strokes.length();i++)draw(c,b,strokes.optJSONObject(i));if(active!=null)draw(c,b,active);c.restore();
        }
        void draw(Canvas c,RectF b,JSONObject s){
            if(s==null)return;JSONArray pts=s.optJSONArray("points");if(pts==null)return;
            paint.setColor(s.optInt("color"));paint.setStrokeCap(Paint.Cap.ROUND);
            for(int i=0;i<pts.length();i++){JSONArray a=pts.optJSONArray(Math.max(0,i-1)),z=pts.optJSONArray(i);float width=(float)s.optDouble("width",3)*Math.max(0.35f,(float)z.optDouble(2,1));paint.setStrokeWidth(width*b.width()/1000);
                float x=b.left+(float)z.optDouble(0)*b.width(),y=b.top+(float)z.optDouble(1)*b.height();
                if(i==0)c.drawCircle(x,y,paint.getStrokeWidth()/2,paint);else c.drawLine(b.left+(float)a.optDouble(0)*b.width(),b.top+(float)a.optDouble(1)*b.height(),x,y,paint);
            }
        }
        void point(float x,float y,float pressure){
            RectF b=bounds();
            if(b.width()>0&&b.height()>0)try {
                points.put(new JSONArray().put((x-b.left)/b.width()).put((y-b.top)/b.height()).put(pressure));
            }catch(JSONException ex){message("No se pudo registrar un punto del trazo");}
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            int action=e.getActionMasked(),index=e.getActionIndex();
            if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
                int tool=e.getToolType(index);if(pointer!=-1||!(tool==MotionEvent.TOOL_TYPE_STYLUS||tool==MotionEvent.TOOL_TYPE_ERASER))return true;
                if(!bounds().contains(e.getX(index),e.getY(index)))return true;
                pointer=e.getPointerId(index);
                if(eraser||tool==MotionEvent.TOOL_TYPE_ERASER){ erase(e.getX(index),e.getY(index));return true;}
                try{points=new JSONArray();active=new JSONObject().put("color",color).put("width",Color.alpha(color)<255?24:3).put("points",points);point(e.getX(index),e.getY(index),e.getPressure(index));}catch(JSONException ex){active=null;}
            }else if(action==MotionEvent.ACTION_MOVE&&pointer!=-1){int i=e.findPointerIndex(pointer);if(i>=0){if(active!=null){for(int h=0;h<e.getHistorySize();h++)point(e.getHistoricalX(i,h),e.getHistoricalY(i,h),e.getHistoricalPressure(i,h));point(e.getX(i),e.getY(i),e.getPressure(i));}else erase(e.getX(i),e.getY(i));}}
            else if(action==MotionEvent.ACTION_CANCEL){active=null;pointer=-1;}
            else if((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)&&e.getPointerId(index)==pointer){commit();pointer=-1;save();}
            invalidate();return true;
        }
        void erase(float x,float y){RectF b=bounds();for(int i=strokes.length()-1;i>=0;i--){JSONArray pts=strokes.optJSONObject(i).optJSONArray("points");for(int j=0;j<pts.length();j++){JSONArray p=pts.optJSONArray(j);if(Math.hypot(b.left+p.optDouble(0)*b.width()-x,b.top+p.optDouble(1)*b.height()-y)<24){strokes.remove(i);break;}}}invalidate();}
    }
}
