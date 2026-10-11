package cl.marilu.notas;

import android.app.*;
import android.content.*;
import android.os.*;
import android.graphics.*;
import android.graphics.pdf.*;
import java.io.*;
import org.json.*;

/** Device smoke checks without an external test framework. */
public class NotesSmokeTest extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    void screenshot(MainActivity a,String name)throws Exception{Bitmap image=getUiAutomation().takeScreenshot();check(image!=null,"Screenshot unavailable");try(OutputStream out=new FileOutputStream(new File(a.getCacheDir(),name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    @Override public void onStart(){Bundle result=new Bundle();MainActivity a=null;try{
        a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final MainActivity app=a;waitForIdleSync();
        File noteFile=new File(a.getFilesDir(),"9000000000001.json");
        JSONObject note=new JSONObject().put("title","Prueba de escritura").put("text","Texto guardado para comprobar el editor.").put("folder","Estudio/Biología").put("favorite",true).put("pageCount",2).put("pages",new JSONObject());
        a.writeNote(noteFile,note);runOnMainSync(()->app.open(noteFile));waitForIdleSync();
        JSONArray strokePoints=new JSONArray().put(new JSONArray().put(.2).put(.5).put(1)).put(new JSONArray().put(.8).put(.5).put(1));JSONObject stroke=new JSONObject().put("color",Color.RED).put("width",8).put("points",strokePoints);
        runOnMainSync(()->{app.ink.history.begin(app.ink.strokes.toString());app.ink.strokes.put(stroke);app.ink.commit();check(app.save(),"Save failed");});waitForIdleSync();
        runOnMainSync(()->{app.ink.history.begin(app.ink.strokes.toString());app.ink.strokes.remove(0);app.ink.commit();app.ink.undo();check(app.ink.strokes.length()==1,"Erase undo lost ink");app.ink.redo();check(app.ink.strokes.length()==0,"Redo failed");app.ink.undo();});
        runOnMainSync(()->app.changePage(1));waitForIdleSync();runOnMainSync(()->app.changePage(-1));waitForIdleSync();
        check(a.ink.strokes.length()==1,"Page navigation lost ink");
        JSONObject persisted=a.read(noteFile);check(persisted.optString("folder").equals("Estudio/Biología"),"Folder missing");check(persisted.getJSONObject("pages").getJSONArray("0").length()==1,"Persisted ink missing");
        File exported=new File(a.getCacheDir(),"native-export.pdf");a.exportPdf(persisted,exported);
        try(PdfRenderer renderer=new PdfRenderer(ParcelFileDescriptor.open(exported,ParcelFileDescriptor.MODE_READ_ONLY))){check(renderer.getPageCount()==3,"Notebook/text page count incorrect");try(PdfRenderer.Page page=renderer.openPage(0)){Bitmap bitmap=Bitmap.createBitmap(page.getWidth(),page.getHeight(),Bitmap.Config.ARGB_8888);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);int pixel=bitmap.getPixel(bitmap.getWidth()/2,bitmap.getHeight()/2);check(Color.red(pixel)>200&&Color.green(pixel)<80,"Exported annotation missing");bitmap.recycle();}}
        screenshot(a,"editor-check.png");
        File sourcePdf=new File(a.getFilesDir(),noteFile.getName()+".pdf");PdfDocument sourceDocument=new PdfDocument();try{for(int i=1;i<=2;i++){PdfDocument.Page page=sourceDocument.startPage(new PdfDocument.PageInfo.Builder(595,842,i).create());page.getCanvas().drawColor(Color.WHITE);sourceDocument.finishPage(page);}try(OutputStream out=new FileOutputStream(sourcePdf)){sourceDocument.writeTo(out);}}finally{sourceDocument.close();}
        persisted.put("pdf",sourcePdf.getName());a.exportPdf(persisted,exported);try(PdfRenderer renderer=new PdfRenderer(ParcelFileDescriptor.open(exported,ParcelFileDescriptor.MODE_READ_ONLY))){check(renderer.getPageCount()==3,"Imported PDF export lost pages");try(PdfRenderer.Page page=renderer.openPage(1)){Bitmap bitmap=Bitmap.createBitmap(595,842,Bitmap.Config.ARGB_8888);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);check(Color.green(bitmap.getPixel(297,421))<80,"PDF overlay missing");bitmap.recycle();}}
        File restored=new File(a.getCacheDir(),"restore-library");restored.mkdirs();ByteArrayOutputStream bytes=new ByteArrayOutputStream();NoteArchives.exportAll(a.getFilesDir(),bytes);int count=NoteArchives.restore(restored,a.getCacheDir(),new ByteArrayInputStream(bytes.toByteArray()));check(count>=1,"Backup not restored");
        runOnMainSync(()->app.library());waitForIdleSync();screenshot(a,"library-check.png");
        result.putString("stream","PASS: editor, save, erase undo/redo, multipage notes, PDF annotation pixels, imported PDF, backup restore.\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("stream","FAIL: "+error+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
