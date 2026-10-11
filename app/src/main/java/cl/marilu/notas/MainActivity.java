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
import android.text.*;
import android.text.style.*;
import android.graphics.pdf.PdfDocument;
import java.util.*;
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition;
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel;
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier;
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer;
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.common.model.DownloadConditions;

/** Initial offline prototype. No account or network permission required. */
public class MainActivity extends Activity {
    LinearLayout root;
    EditText title, text;
    Ink ink;
    File current;
    PdfRenderer pdf;
    int page;
    JSONObject document;
    String filter="Todas", search="";
    boolean sortTitle=false, loading=false;
    File pendingExport;
    DigitalInkRecognizer recognizer;
    final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    final Runnable autosave=()->save();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(state!=null&&state.containsKey("export"))pendingExport=new File(getCacheDir(),state.getString("export"));
        if(state!=null && state.containsKey("note")) {
            open(new File(getFilesDir(),state.getString("note")));
            page=state.getInt("page",0);
        } else library();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        save();
        if(pendingExport!=null)state.putString("export",pendingExport.getName());
        if(current!=null){state.putString("note",current.getName());state.putInt("page",page);}
        super.onSaveInstanceState(state);
    }
    void message(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    Button button(String label, Runnable action) {
        Button b=new Button(this); b.setText(label); b.setOnClickListener(v->action.run()); return b;
    }
    void prompt(String heading,String value,java.util.function.Consumer<String> done){EditText input=new EditText(this);input.setText(value);new AlertDialog.Builder(this).setTitle(heading).setView(input).setPositiveButton("Aceptar",(d,w)->done.accept(input.getText().toString().trim())).setNegativeButton("Cancelar",null).show();}
    void picker(int code,String type,String name){Intent i=new Intent(name==null?Intent.ACTION_OPEN_DOCUMENT:Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(type);if(name!=null)i.putExtra(Intent.EXTRA_TITLE,name);try{startActivityForResult(i,code);}catch(ActivityNotFoundException e){message("No hay un selector de archivos disponible");}}
    void work(String label,Runnable task){message(label);new Thread(task,"notes-files").start();}
    void error(Exception e){runOnUiThread(()->message(e.getMessage()==null?"No se pudo completar la operación":e.getMessage()));}
    void layout() {
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16,16,16,16); root.setBackgroundColor(Color.rgb(246,244,240));
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(16+insets.getSystemWindowInsetLeft(),16+insets.getSystemWindowInsetTop(),16+insets.getSystemWindowInsetRight(),16+insets.getSystemWindowInsetBottom());return insets;});setContentView(root);
    }
    void library() {
        closePdf(); current=null; ink=null; layout();
        TextView heading=new TextView(this); heading.setText("Mis notas"); heading.setTextSize(28); root.addView(heading);
        root.addView(button("+ Nueva nota",()->open(new File(getFilesDir(),System.currentTimeMillis()+".json"))));
        HorizontalScrollView actions=new HorizontalScrollView(this);LinearLayout buttons=new LinearLayout(this);actions.addView(buttons);root.addView(actions);
        buttons.addView(button("Abrir PDF / Drive",()->{open(new File(getFilesDir(),System.currentTimeMillis()+".json"));picker(10,"application/pdf",null);}));
        buttons.addView(button("Copia / Drive",()->prepareBackup()));
        buttons.addView(button("Restaurar copia",()->picker(12,"application/zip",null)));
        buttons.addView(button("Ordenar",()->{sortTitle=!sortTitle;library();}));
        buttons.addView(button("Carpetas",()->chooseFilter()));
        EditText query=new EditText(this);query.setSingleLine(true);query.setHint("Buscar título, texto o etiquetas");query.setText(search);root.addView(query);
        TextView selected=new TextView(this);selected.setText(filter+" · "+(sortTitle?"por título":"más recientes"));root.addView(selected);
        ScrollView scroll=new ScrollView(this); LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list); root.addView(scroll);
        fillLibrary(list);
        query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){search=s.toString();fillLibrary(list);}public void afterTextChanged(Editable e){}});
    }
    void fillLibrary(LinearLayout list){list.removeAllViews();
        File[] files=getFilesDir().listFiles((d,n)->n.endsWith(".json"));
        if(files!=null) { java.util.Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
            if(sortTitle)java.util.Arrays.sort(files,(a,b)->{try{return read(a).optString("title").compareToIgnoreCase(read(b).optString("title"));}catch(Exception e){return 0;}});
            for(File f:files) try { JSONObject j=read(f);
                boolean trash=j.optBoolean("trash");if(trash!=filter.equals("Papelera"))continue;
                if(filter.equals("Favoritos")&&!j.optBoolean("favorite"))continue;
                if(!filter.equals("Todas")&&!filter.equals("Favoritos")&&!filter.equals("Papelera")&&!j.optString("folder").equals(filter)&&!j.optString("folder").startsWith(filter+"/"))continue;
                String hay=j.optString("title")+" "+j.optString("text")+" "+j.optString("tags");if(!hay.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))continue;
                Button item=button((j.optBoolean("favorite")?"★ ":"")+j.optString("title","Sin título")+(j.optString("folder").isEmpty()?"":" · "+j.optString("folder")),()->{if(trash)noteActions(f);else open(f);});item.setOnLongClickListener(v->{noteActions(f);return true;});list.addView(item);
            }
            catch(Exception e) { list.addView(button("Nota que requiere revisión",()->message("No se pudo leer "+f.getName()))); }
        }
    }
    void chooseFilter(){TreeSet<String> choices=new TreeSet<>();File[] files=getFilesDir().listFiles((d,n)->n.endsWith(".json"));if(files!=null)for(File f:files)try{String folder=read(f).optString("folder");if(!folder.isEmpty())choices.add(folder);}catch(Exception ignored){}ArrayList<String> names=new ArrayList<>(Arrays.asList("Todas","Favoritos","Papelera"));names.addAll(choices);new AlertDialog.Builder(this).setTitle("Carpetas y colecciones").setItems(names.toArray(new String[0]),(d,w)->{filter=names.get(w);library();}).show();}
    void writeNote(File f,JSONObject j)throws Exception{android.util.AtomicFile file=new android.util.AtomicFile(f);FileOutputStream out=null;try{out=file.startWrite();out.write(j.toString().getBytes("UTF-8"));file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}}
    void noteActions(File f){try{JSONObject j=read(f);String[] options=j.optBoolean("trash")?new String[]{"Restaurar nota"}:new String[]{"Cambiar carpeta","Etiquetas","Favorito","Duplicar","Mover a papelera"};new AlertDialog.Builder(this).setTitle(j.optString("title","Nota")).setItems(options,(d,w)->{
        try{if(j.optBoolean("trash")){j.put("trash",false);writeNote(f,j);library();return;}
            if(w==0||w==1){String key=w==0?"folder":"tags";prompt(w==0?"Carpeta (ej.: Estudio/Biología)":"Etiquetas separadas por comas",j.optString(key),value->{try{j.put(key,value);writeNote(f,j);library();}catch(Exception e){error(e);}});}
            else if(w==2){j.put("favorite",!j.optBoolean("favorite"));writeNote(f,j);library();}
            else if(w==3){File copy=new File(getFilesDir(),System.currentTimeMillis()+".json");if(copy.exists())throw new IOException("Vuelve a intentar la duplicación");String pdfName=j.optString("pdf");if(!pdfName.isEmpty()){java.nio.file.Files.copy(new File(getFilesDir(),pdfName).toPath(),new File(getFilesDir(),copy.getName()+".pdf").toPath());j.put("pdf",copy.getName()+".pdf");}j.put("title",j.optString("title")+" (copia)");writeNote(copy,j);library();}
            else{j.put("trash",true);writeNote(f,j);library();}
        }catch(Exception e){error(e);}
    }).show();}catch(Exception e){error(e);}}
    JSONObject read(File f) throws Exception {
        if(!f.exists()) return new JSONObject();
        try(FileInputStream in=new FileInputStream(f); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int n; while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            return new JSONObject(out.toString("UTF-8"));
        }
    }
    void open(File f) {
        try { document=read(f); } catch(Exception e) { message("No se pudo abrir la nota"); return; }
        current=f; page=0; layout();loading=true;
        title=new EditText(this); title.setSingleLine(true); title.setHint("Título de la nota"); title.setText(document.optString("title","")); root.addView(title);
        LinearLayout row=new LinearLayout(this); root.addView(row);
        row.addView(button("Notas",()->{if(save())library();}));
        row.addView(button("PDF",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("application/pdf"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,10);}));
        row.addView(button("Guardar",()->{if(save())message("Nota guardada");}));
        row.addView(button("Más",()->editorMenu()));
        text=new EditText(this); text.setHint("Escribe aquí con el teclado…");text.setText(document.has("richText")?Html.fromHtml(document.optString("richText"),Html.FROM_HTML_MODE_COMPACT):document.optString("text","")); text.setMinLines(2); text.setMaxLines(4); text.setGravity(Gravity.TOP); root.addView(text);
        HorizontalScrollView tools=new HorizontalScrollView(this); LinearLayout toolbar=new LinearLayout(this); tools.addView(toolbar); root.addView(tools);
        ink=new Ink();
        toolbar.addView(button("Lápiz",()->{ink.setTool("pen"); ink.color=Color.rgb(36,42,53);}));
        toolbar.addView(button("Azul",()->{ink.setTool("pen"); ink.color=Color.rgb(40,92,185);}));
        toolbar.addView(button("Resaltar",()->{ink.setTool("pen"); ink.color=0x66FFD54F;}));
        toolbar.addView(button("Borrar trazo",()->ink.setTool("erase")));
        toolbar.addView(button("Borrar área",()->ink.setTool("area")));
        toolbar.addView(button("Deshacer",()->ink.undo()));
        toolbar.addView(button("Rehacer",()->ink.redo()));
        toolbar.addView(button("Mover hoja",()->ink.setTool("pan")));
        toolbar.addView(button("Seleccionar",()->ink.setTool("select")));
        toolbar.addView(button("‹ Página",()->changePage(-1)));
        toolbar.addView(button("Página ›",()->changePage(1)));
        root.addView(ink,new LinearLayout.LayoutParams(-1,0,1));
        String path=document.optString("pdf","");
        if(!path.isEmpty()) try { pdf=new PdfRenderer(ParcelFileDescriptor.open(new File(getFilesDir(),path),ParcelFileDescriptor.MODE_READ_ONLY)); }
        catch(Exception e) { message("No se pudo cargar el PDF"); }
        ink.post(()->{ink.load(); render();});
        TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){if(!loading){handler.removeCallbacks(autosave);handler.postDelayed(autosave,700);}}public void afterTextChanged(Editable e){}};
        title.addTextChangedListener(watcher);text.addTextChangedListener(watcher);loading=false;
    }
    void changePage(int delta) {
        int count=pdf==null?document.optInt("pageCount",1):pdf.getPageCount();int next=page+delta; if(next<0||next>=count)return;
        if(!save())return; page=next; ink.load(); render(); message("Página "+(page+1)+" de "+count);
    }
    void render() {
        if(ink.background!=null){ink.background.recycle();ink.background=null;}
        if(pdf!=null) try(PdfRenderer.Page p=pdf.openPage(page)) {
            float scale=Math.min(Math.min(1600f/p.getWidth(),2200f/p.getHeight()),Math.max(600,ink.getWidth())/(float)p.getWidth());int w=Math.max(1,Math.round(p.getWidth()*scale)),h=Math.max(1,Math.round(p.getHeight()*scale));
            ink.background=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); ink.background.eraseColor(Color.WHITE);
            p.render(ink.background,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        } catch(Exception e){message("No se pudo mostrar la página");}
        ink.invalidate();
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data); if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(request==11){if(pendingExport==null||!pendingExport.isFile()){message("Vuelve a exportar el archivo");return;}File source=pendingExport;work("Guardando archivo…",()->{try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(data.getData(),"w")){if(out==null)throw new IOException("No se pudo abrir el destino");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);runOnUiThread(()->message("Archivo enviado al destino elegido"));}catch(Exception e){error(e);}});return;}
        if(request==12){work("Restaurando copia…",()->{try(InputStream in=getContentResolver().openInputStream(data.getData())){if(in==null)throw new IOException("No se pudo abrir la copia");int count=NoteArchives.restore(getFilesDir(),getCacheDir(),in);runOnUiThread(()->{if(current==null)library();message(count+" notas restauradas sin sobrescribir las existentes");});}catch(Exception e){error(e);}});return;}
        if(request==13){importImage(data.getData());return;}
        if(request!=10)return;
        importPdf(data.getData());
    }
    void importPdf(android.net.Uri uri){if(pdf!=null){message("Crea una nota nueva para importar otro PDF");return;}File note=current;if(note==null)return;File dest=new File(getFilesDir(),note.getName()+".pdf");
        work("Importando PDF desde el destino elegido…",()->{PdfRenderer opened=null;try{
            try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(dest)){if(in==null)throw new IOException("No se pudo abrir el PDF");byte[] b=new byte[8192];int n;long total=0;while((n=in.read(b))!=-1){total+=n;if(total>32L*1024*1024)throw new IOException("El PDF supera el límite de 32 MB de esta versión");out.write(b,0,n);}}
            opened=new PdfRenderer(ParcelFileDescriptor.open(dest,ParcelFileDescriptor.MODE_READ_ONLY));PdfRenderer ready=opened;
            runOnUiThread(()->{if(isDestroyed()||current==null||!current.equals(note)){ready.close();dest.delete();return;}try{pdf=ready;document.put("pdf",dest.getName());page=0;save();render();message("PDF importado: "+pdf.getPageCount()+" páginas");}catch(Exception e){closePdf();error(e);}});
        }catch(Exception e){if(opened!=null)opened.close();dest.delete();error(e);}});
    }
    boolean save() {
        if(current==null||ink==null)return true;
        android.util.AtomicFile file=new android.util.AtomicFile(current); FileOutputStream out=null;
        try {
            ink.commit(); document.put("title",title.getText().toString().trim().isEmpty()?"Sin título":title.getText().toString());
            document.put("text",text.getText().toString());document.put("richText",Html.toHtml(text.getText(),Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)); JSONObject pages=document.optJSONObject("pages");if(pages==null){pages=new JSONObject();document.put("pages",pages);} pages.put(""+page,ink.strokes);
            out=file.startWrite();out.write(document.toString().getBytes("UTF-8"));file.finishWrite(out);return true;
        } catch(Exception e){if(out!=null)file.failWrite(out);message("No se pudo guardar la nota");return false;}
    }
    void closePdf(){if(pdf!=null){pdf.close();pdf=null;}if(ink!=null){ink.clearImages();if(ink.background!=null){ink.background.recycle();ink.background=null;}}}
    @Override protected void onPause(){handler.removeCallbacks(autosave);save();super.onPause();}
    @Override protected void onDestroy(){closePdf();if(recognizer!=null)recognizer.close();super.onDestroy();}
    @Override public void onBackPressed(){if(current!=null){if(save())library();}else super.onBackPressed();}

    void editorMenu(){String[] entries={"Guardar PDF / Drive","Guardar texto / Drive","Copia de seguridad / Drive","Carpeta y etiquetas","Color del lápiz","Grosor del lápiz","Plantilla de hoja","Escribir también con el dedo","Añadir página","Duplicar página","Mover página","Eliminar página","Texto: negrita","Texto: cursiva","Texto: subrayado","Línea recta","Rectángulo","Óvalo","Copiar selección","Pegar selección","Eliminar selección","Cambiar tamaño de selección","Ajustar zoom","Manuscrito a texto (español)","Insertar imagen / Drive","Guardar nota editable / Drive"};new AlertDialog.Builder(this).setTitle("Herramientas de la nota").setItems(entries,(d,w)->{
        switch(w){
        case 0:preparePdf();break;case 1:prepareText();break;case 2:prepareBackup();break;case 3:if(save())noteActions(current);break;
        case 4:String[] colors={"Negro","Azul","Rojo","Verde","Violeta"};int[] values={0xff242a35,0xff285cb9,0xffc93535,0xff257a49,0xff7c43ad};new AlertDialog.Builder(this).setTitle("Color").setItems(colors,(x,y)->{ink.color=values[y];ink.setTool("pen");}).show();break;
        case 5:new AlertDialog.Builder(this).setTitle("Grosor").setItems(new String[]{"Fino","Medio","Grueso"},(x,y)->ink.width=new float[]{2,4,8}[y]).show();break;
        case 6:new AlertDialog.Builder(this).setTitle("Plantilla").setItems(new String[]{"Blanca","Rayada","Cuadrícula","Puntos"},(x,y)->{if(pdf!=null){message("La plantilla no cambia el fondo del PDF");return;}try{document.put("template",y);save();ink.invalidate();}catch(Exception e){error(e);}}).show();break;
        case 7:ink.finger=!ink.finger;message(ink.finger?"Dedo y lápiz activos":"Solo lápiz: el dedo no dibuja");break;
        case 8:if(pdf!=null){message("Las páginas del PDF se conservan en este formato");return;}if(save())try{page=document.optInt("pageCount",1);document.put("pageCount",page+1);ink.load();render();save();}catch(Exception e){error(e);}break;
        case 9:editPage("duplicate");break;case 10:editPage("move");break;case 11:editPage("delete");break;
        case 12:formatText(new StyleSpan(Typeface.BOLD));break;case 13:formatText(new StyleSpan(Typeface.ITALIC));break;case 14:formatText(new UnderlineSpan());break;
        case 15:ink.setTool("line");break;case 16:ink.setTool("rect");break;case 17:ink.setTool("oval");break;
        case 18:ink.copySelection();break;case 19:ink.pasteSelection();break;case 20:ink.deleteSelection();break;case 21:ink.resizeSelection();break;case 22:ink.zoom=1;ink.offsetX=ink.offsetY=0;ink.invalidate();break;
        case 23:recognizeWriting();break;
        case 24:picker(13,"image/*",null);break;
        case 25:prepareNoteArchive();break;
        }
    }).show();}
    void importImage(android.net.Uri uri){File note=current;int targetPage=page;work("Preparando imagen…",()->{try{
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("No se pudo abrir la imagen");BitmapFactory.decodeStream(in,null,options);}
        if(options.outWidth<=0||options.outHeight<=0)throw new IOException("Formato de imagen no compatible");options.inJustDecodeBounds=false;options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>1600)options.inSampleSize*=2;
        Bitmap bitmap;try(InputStream in=getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,options);}if(bitmap==null)throw new IOException("No se pudo leer la imagen");
        float ratio=bitmap.getHeight()/(float)bitmap.getWidth();ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();String image=android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
        runOnUiThread(()->{if(current==null||!current.equals(note)||page!=targetPage){message("Vuelve a insertar la imagen en la página deseada");return;}try{float pageRatio=ink.background==null?1.414f:ink.background.getHeight()/(float)ink.background.getWidth();float height=Math.min(.65f,.6f*ratio/pageRatio),width=height*pageRatio/ratio;ink.history.begin(ink.strokes.toString());ink.strokes.put(new JSONObject().put("image",image).put("points",new JSONArray().put(new JSONArray().put(.15).put(.15)).put(new JSONArray().put(.15+width).put(.15+height))));ink.commit();save();message("Imagen insertada; usa Seleccionar para moverla o cambiar su tamaño");}catch(Exception e){error(e);}});
    }catch(Exception e){error(e);}});}
    void recognizeWriting(){if(!save())return;try{
        com.google.mlkit.vision.digitalink.recognition.Ink.Builder builder=com.google.mlkit.vision.digitalink.recognition.Ink.builder();int count=0;
        for(int i=0;i<ink.strokes.length();i++){
            JSONObject s=ink.strokes.getJSONObject(i);if(!ink.selected.isEmpty()&&!ink.selected.contains(i))continue;if(s.has("image")||!s.optString("shape").isEmpty()||Color.alpha(s.optInt("color"))<255)continue;
            JSONArray pts=s.getJSONArray("points");com.google.mlkit.vision.digitalink.recognition.Ink.Stroke.Builder stroke=com.google.mlkit.vision.digitalink.recognition.Ink.Stroke.builder();for(int p=0;p<pts.length();p++){JSONArray point=pts.getJSONArray(p);stroke.addPoint(com.google.mlkit.vision.digitalink.recognition.Ink.Point.create((float)point.getDouble(0)*1000,(float)point.getDouble(1)*1414));}if(pts.length()>0){builder.addStroke(stroke.build());count++;}
        }
        if(count==0){message("Escribe con el lápiz o selecciona escritura para convertirla");return;}
        DigitalInkRecognitionModelIdentifier identifier=DigitalInkRecognitionModelIdentifier.fromLanguageTag("es");if(identifier==null)throw new IOException("No se encontró un modelo para español");
        DigitalInkRecognitionModel model=DigitalInkRecognitionModel.builder(identifier).build();RemoteModelManager manager=RemoteModelManager.getInstance();File note=current;
        Runnable recognize=()->{if(isDestroyed())return;if(recognizer!=null)recognizer.close();recognizer=DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build());message("Reconociendo escritura en la tablet…");recognizer.recognize(builder.build()).addOnSuccessListener(result->{if(isDestroyed()||current==null||!current.equals(note))return;if(result.getCandidates().isEmpty()){message("No se reconoció texto; prueba con una selección más pequeña");return;}String candidate=result.getCandidates().get(0).getText();EditText preview=new EditText(this);preview.setText(candidate);new AlertDialog.Builder(this).setTitle("Revisa el texto reconocido").setView(preview).setPositiveButton("Añadir al texto",(d,w)->{text.append((text.length()>0?"\n":"")+preview.getText());save();}).setNegativeButton("Cancelar",null).show();}).addOnFailureListener(e->error(e));};
        manager.isModelDownloaded(model).addOnSuccessListener(available->{if(isDestroyed())return;if(available)recognize.run();else new AlertDialog.Builder(this).setTitle("Reconocimiento local en español").setMessage("Primero se descargará el modelo de Google (aprox. 20 MB). Después la escritura se reconoce en la tablet. ¿Descargarlo ahora?").setPositiveButton("Descargar",(d,w)->manager.download(model,new DownloadConditions.Builder().build()).addOnSuccessListener(v->recognize.run()).addOnFailureListener(e->error(e))).setNegativeButton("Cancelar",null).show();}).addOnFailureListener(e->error(e));
    }catch(Exception e){error(e);}}
    void formatText(Object span){int start=text.getSelectionStart(),end=text.getSelectionEnd();if(start<0||end<=start){message("Selecciona primero el texto que quieres dar formato");return;}text.getText().setSpan(span,start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);save();}
    void editPage(String action){if(pdf!=null){message("La edición del orden del PDF requiere una versión posterior");return;}if(!save())return;int count=document.optInt("pageCount",1);if(action.equals("delete")&&count==1){message("La nota necesita al menos una página");return;}
        java.util.function.IntConsumer apply=target->{try{JSONObject source=document.getJSONObject("pages"),newPages=new JSONObject();ArrayList<JSONArray> list=new ArrayList<>();for(int i=0;i<count;i++){JSONArray a=source.optJSONArray(""+i);list.add(a==null?new JSONArray():a);}if(action.equals("duplicate")){list.add(page+1,new JSONArray(list.get(page).toString()));page++;}else if(action.equals("delete")){list.remove(page);page=Math.min(page,list.size()-1);}else{JSONArray a=list.remove(page);list.add(target,a);page=target;}for(int i=0;i<list.size();i++)newPages.put(""+i,list.get(i));document.put("pages",newPages).put("pageCount",list.size());ink.load();render();save();}catch(Exception e){error(e);}};
        if(action.equals("move")){String[] items=new String[count];for(int i=0;i<count;i++)items[i]="Página "+(i+1);new AlertDialog.Builder(this).setTitle("Mover a posición").setItems(items,(d,w)->apply.accept(w)).show();}
        else if(action.equals("delete"))new AlertDialog.Builder(this).setMessage("¿Eliminar esta página y sus trazos?").setPositiveButton("Eliminar",(d,w)->apply.accept(0)).setNegativeButton("Cancelar",null).show();else apply.accept(0);
    }
    void prepareBackup(){if(!save())return;work("Preparando copia completa…",()->{try{File f=File.createTempFile("mis-notas-",".zip",getCacheDir());try(OutputStream out=new FileOutputStream(f)){NoteArchives.exportAll(getFilesDir(),out);}runOnUiThread(()->{pendingExport=f;picker(11,"application/zip","Mis-notas-copia.zip");});}catch(Exception e){error(e);}});}
    void prepareNoteArchive(){if(!save())return;File note=current;work("Preparando nota editable…",()->{try{File f=File.createTempFile("mis-notas-",".zip",getCacheDir());try(OutputStream out=new FileOutputStream(f)){NoteArchives.exportNote(getFilesDir(),note,out);}runOnUiThread(()->{pendingExport=f;picker(11,"application/zip","Mis-notas-editable.zip");});}catch(Exception e){error(e);}});}
    void prepareText(){if(!save())return;try{pendingExport=File.createTempFile("nota-",".txt",getCacheDir());try(OutputStream out=new FileOutputStream(pendingExport)){out.write((title.getText()+"\n\n"+text.getText()).getBytes("UTF-8"));}picker(11,"text/plain","Mis-notas.txt");}catch(Exception e){error(e);}}
    void preparePdf(){if(!save())return;final String snapshot=document.toString();work("Preparando PDF con anotaciones…",()->{try{JSONObject note=new JSONObject(snapshot);File f=File.createTempFile("nota-",".pdf",getCacheDir());exportPdf(note,f);runOnUiThread(()->{pendingExport=f;picker(11,"application/pdf","Mis-notas.pdf");});}catch(Exception e){error(e);}});}
    void exportPdf(JSONObject note,File file)throws Exception{
        String name=note.optString("pdf");PdfRenderer renderer=null;
        PdfDocument output=new PdfDocument();try{
            if(!name.isEmpty()){if(!name.matches("[0-9]+\\.json\\.pdf"))throw new IOException("Referencia PDF no válida");renderer=new PdfRenderer(ParcelFileDescriptor.open(new File(getFilesDir(),name),ParcelFileDescriptor.MODE_READ_ONLY));}
            int count=renderer==null?note.optInt("pageCount",1):renderer.getPageCount();JSONObject pages=note.optJSONObject("pages");int number=1;
            if(renderer!=null&&!note.optString("text").trim().isEmpty())number=exportTextPages(output,note,number);
            for(int i=0;i<count;i++){
                int w=1000,h=1414;PdfRenderer.Page source=renderer==null?null:renderer.openPage(i);
                if(source!=null){w=source.getWidth();h=source.getHeight();}
                PdfDocument.Page target=output.startPage(new PdfDocument.PageInfo.Builder(w,h,number++).create());Canvas c=target.getCanvas();c.drawColor(Color.WHITE);
                if(source!=null){float scale=Math.min(2,Math.min(1600f/w,2200f/h));Bitmap bitmap=Bitmap.createBitmap(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)),Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);source.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT);c.drawBitmap(bitmap,null,new Rect(0,0,w,h),null);bitmap.recycle();source.close();}
                else drawTemplate(c,new RectF(0,0,w,h),note.optInt("template",1));
                JSONArray strokes=pages==null?null:pages.optJSONArray(""+i);if(strokes!=null)for(int s=0;s<strokes.length();s++)drawStroke(c,new RectF(0,0,w,h),strokes.optJSONObject(s),new Paint(Paint.ANTI_ALIAS_FLAG));
                output.finishPage(target);
            }
            if(renderer==null&&!note.optString("text").trim().isEmpty())exportTextPages(output,note,number);
            try(OutputStream out=new FileOutputStream(file)){output.writeTo(out);}
        }finally{try{output.close();}finally{if(renderer!=null)renderer.close();}}
    }
    int exportTextPages(PdfDocument pdf,JSONObject note,int number){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.rgb(36,42,53));p.setTextSize(18);String value=note.optString("title")+"\n\n"+note.optString("text");ArrayList<String> lines=new ArrayList<>();for(String paragraph:value.split("\n",-1)){if(paragraph.isEmpty()){lines.add("");continue;}int start=0;while(start<paragraph.length()){int n=p.breakText(paragraph,start,paragraph.length(),true,500,null);if(n<=0)n=1;lines.add(paragraph.substring(start,start+n));start+=n;}}for(int i=0;i<lines.size();){PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,number++).create());Canvas c=page.getCanvas();c.drawColor(Color.WHITE);for(int y=55;y<790&&i<lines.size();y+=26)c.drawText(lines.get(i++),45,y,p);pdf.finishPage(page);}return number;}
    static void drawTemplate(Canvas c,RectF b,int style){Paint p=new Paint();p.setColor(0xffe5e9ef);p.setStrokeWidth(1);float step=b.width()/25;if(style==1||style==2)for(float y=b.top+step;y<b.bottom;y+=step)c.drawLine(b.left,y,b.right,y,p);if(style==2)for(float x=b.left+step;x<b.right;x+=step)c.drawLine(x,b.top,x,b.bottom,p);if(style==3)for(float y=b.top+step;y<b.bottom;y+=step)for(float x=b.left+step;x<b.right;x+=step)c.drawCircle(x,y,1.4f,p);}
    static RectF imageBounds(RectF b,JSONArray points){JSONArray a=points.optJSONArray(0),z=points.optJSONArray(points.length()-1);return new RectF(b.left+(float)a.optDouble(0)*b.width(),b.top+(float)a.optDouble(1)*b.height(),b.left+(float)z.optDouble(0)*b.width(),b.top+(float)z.optDouble(1)*b.height());}
    static Bitmap decodeImage(String value){try{byte[] bytes=android.util.Base64.decode(value,android.util.Base64.DEFAULT);return BitmapFactory.decodeByteArray(bytes,0,bytes.length);}catch(IllegalArgumentException e){return null;}}
    static void drawStroke(Canvas c,RectF b,JSONObject s,Paint paint){if(s==null)return;JSONArray pts=s.optJSONArray("points");if(pts==null||pts.length()==0)return;if(s.has("image")){Bitmap bitmap=decodeImage(s.optString("image"));if(bitmap!=null){c.drawBitmap(bitmap,null,imageBounds(b,pts),null);bitmap.recycle();}return;}paint.setColor(s.optInt("color"));paint.setStrokeCap(Paint.Cap.ROUND);String shape=s.optString("shape");float width=(float)s.optDouble("width",3)*b.width()/1000;paint.setStrokeWidth(width);
        if(!shape.isEmpty()){JSONArray first=pts.optJSONArray(0),last=pts.optJSONArray(pts.length()-1);float x1=b.left+(float)first.optDouble(0)*b.width(),y1=b.top+(float)first.optDouble(1)*b.height(),x2=b.left+(float)last.optDouble(0)*b.width(),y2=b.top+(float)last.optDouble(1)*b.height();paint.setStyle(Paint.Style.STROKE);if(shape.equals("line"))c.drawLine(x1,y1,x2,y2,paint);else{RectF r=new RectF(Math.min(x1,x2),Math.min(y1,y2),Math.max(x1,x2),Math.max(y1,y2));if(shape.equals("rect"))c.drawRect(r,paint);else c.drawOval(r,paint);}paint.setStyle(Paint.Style.FILL);return;}
        for(int i=0;i<pts.length();i++){JSONArray a=pts.optJSONArray(Math.max(0,i-1)),z=pts.optJSONArray(i);paint.setStrokeWidth(width*Math.max(0.35f,(float)z.optDouble(2,1)));float x=b.left+(float)z.optDouble(0)*b.width(),y=b.top+(float)z.optDouble(1)*b.height();if(i==0)c.drawCircle(x,y,paint.getStrokeWidth()/2,paint);else c.drawLine(b.left+(float)a.optDouble(0)*b.width(),b.top+(float)a.optDouble(1)*b.height(),x,y,paint);}
    }

    class Ink extends View {
        JSONArray strokes=new JSONArray(), points;
        JSONObject active;
        Bitmap background;
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        int color=Color.rgb(36,42,53), pointer=-1;
        boolean finger=false,moving=false;
        String tool="pen";
        float width=3,zoom=1,offsetX=0,offsetY=0,lastX,lastY;
        RectF selection;
        HashSet<Integer> selected=new HashSet<>();
        JSONArray clipboard=new JSONArray();
        EditHistory history=new EditHistory();
        ScaleGestureDetector scale;
        IdentityHashMap<JSONObject,Bitmap> images=new IdentityHashMap<>();
        void clearImages(){for(Bitmap b:images.values())if(b!=null)b.recycle();images.clear();}
        Ink(){super(MainActivity.this);setBackgroundColor(Color.WHITE);scale=new ScaleGestureDetector(MainActivity.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){float previous=zoom;zoom=Math.max(1,Math.min(5,zoom*d.getScaleFactor()));RectF base=baseBounds();offsetX=d.getFocusX()-base.left-(d.getFocusX()-base.left-offsetX)*zoom/previous;offsetY=d.getFocusY()-base.top-(d.getFocusY()-base.top-offsetY)*zoom/previous;invalidate();return true;}});}
        void setTool(String value){commit();tool=value;if(!value.equals("select")){selection=null;selected.clear();}invalidate();}
        void load(){clearImages();JSONObject pages=document.optJSONObject("pages");strokes=pages==null?null:pages.optJSONArray(""+page);if(strokes==null)strokes=new JSONArray();active=null;pointer=-1;history.clear();selected.clear();selection=null;zoom=1;offsetX=offsetY=0;invalidate();}
        void commit(){if(active!=null){strokes.put(active);active=null;}history.finish(strokes.toString());pointer=-1;invalidate();}
        void restore(String value){clearImages();try{strokes=new JSONArray(value);}catch(JSONException e){message("No se pudo recuperar la edición");}selected.clear();selection=null;invalidate();}
        void undo(){commit();restore(history.undo(strokes.toString()));save();}
        void redo(){commit();restore(history.redo(strokes.toString()));save();}
        void copySelection(){clipboard=new JSONArray();for(int i:selected)try{clipboard.put(new JSONObject(strokes.getJSONObject(i).toString()));}catch(JSONException e){error(e);}message(clipboard.length()+" trazos copiados");}
        void pasteSelection(){history.begin(strokes.toString());for(int i=0;i<clipboard.length();i++)try{JSONObject s=new JSONObject(clipboard.getJSONObject(i).toString());JSONArray pts=s.getJSONArray("points");for(int j=0;j<pts.length();j++){JSONArray p=pts.getJSONArray(j);p.put(0,p.getDouble(0)+.03);p.put(1,p.getDouble(1)+.03);}strokes.put(s);}catch(JSONException e){error(e);}commit();save();}
        void deleteSelection(){history.begin(strokes.toString());ArrayList<Integer> indexes=new ArrayList<>(selected);java.util.Collections.sort(indexes,java.util.Collections.reverseOrder());for(int i:indexes)strokes.remove(i);selected.clear();selection=null;commit();save();}
        void resizeSelection(){if(selected.isEmpty()){message("Selecciona los trazos primero");return;}new AlertDialog.Builder(MainActivity.this).setTitle("Tamaño de la selección").setItems(new String[]{"75 %","125 %","150 %"},(d,w)->{float factor=new float[]{.75f,1.25f,1.5f}[w];history.begin(strokes.toString());RectF b=bounds();float cx=(selection.centerX()-b.left)/b.width(),cy=(selection.centerY()-b.top)/b.height();for(int i:selected)try{JSONArray pts=strokes.getJSONObject(i).getJSONArray("points");for(int j=0;j<pts.length();j++){JSONArray p=pts.getJSONArray(j);p.put(0,cx+(p.getDouble(0)-cx)*factor);p.put(1,cy+(p.getDouble(1)-cy)*factor);}}catch(JSONException e){error(e);}selected.clear();selection=null;commit();save();}).show();}
        RectF baseBounds(){float ratio=background==null?1414f/1000:background.getHeight()/(float)background.getWidth();float w=Math.min(getWidth(),getHeight()/ratio),h=w*ratio;return new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);}
        RectF bounds(){
            RectF b=baseBounds();return new RectF(b.left+offsetX,b.top+offsetY,b.left+offsetX+b.width()*zoom,b.top+offsetY+b.height()*zoom);
        }
        @Override protected void onDraw(Canvas c){
            RectF b=bounds(); if(background!=null)c.drawBitmap(background,null,b,null);
            else{c.save();c.clipRect(b);drawTemplate(c,b,document.optInt("template",1));c.restore();}
            c.save();c.clipRect(b);for(int i=0;i<strokes.length();i++)draw(c,b,strokes.optJSONObject(i));if(active!=null)draw(c,b,active);c.restore();
            if(selection!=null){paint.setColor(0xff285cb9);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setPathEffect(new DashPathEffect(new float[]{8,5},0));c.drawRect(selection,paint);paint.setPathEffect(null);paint.setStyle(Paint.Style.FILL);}
        }
        void draw(Canvas c,RectF b,JSONObject s){
            if(s!=null&&s.has("image")){if(!images.containsKey(s))images.put(s,decodeImage(s.optString("image")));Bitmap bitmap=images.get(s);JSONArray points=s.optJSONArray("points");if(bitmap!=null&&points!=null&&points.length()>1)c.drawBitmap(bitmap,null,imageBounds(b,points),null);return;}
            drawStroke(c,b,s,paint);
        }
        void point(float x,float y,float pressure){
            RectF b=bounds();
            if(b.width()>0&&b.height()>0)try {
                points.put(new JSONArray().put((x-b.left)/b.width()).put((y-b.top)/b.height()).put(pressure));
            }catch(JSONException ex){message("No se pudo registrar un punto del trazo");}
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            int action=e.getActionMasked(),index=e.getActionIndex();
            if(pointer==-1&&e.getToolType(0)==MotionEvent.TOOL_TYPE_FINGER){scale.onTouchEvent(e);if(e.getPointerCount()>1||scale.isInProgress())return true;}
            if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
                int input=e.getToolType(index);if(pointer!=-1||!(input==MotionEvent.TOOL_TYPE_STYLUS||input==MotionEvent.TOOL_TYPE_ERASER||input==MotionEvent.TOOL_TYPE_FINGER&&(finger||tool.equals("pan"))))return true;
                if(!bounds().contains(e.getX(index),e.getY(index)))return true;
                pointer=e.getPointerId(index);
                lastX=e.getX(index);lastY=e.getY(index);history.begin(strokes.toString());
                if(tool.equals("pan"))return true;
                if(tool.equals("select")){moving=selection!=null&&selection.contains(lastX,lastY)&&!selected.isEmpty();if(!moving){selection=new RectF(lastX,lastY,lastX,lastY);selected.clear();}return true;}
                if(tool.equals("erase")||tool.equals("area")||input==MotionEvent.TOOL_TYPE_ERASER){erase(lastX,lastY);return true;}
                try{points=new JSONArray();active=new JSONObject().put("color",color).put("width",Color.alpha(color)<255?24:width).put("points",points);if(!tool.equals("pen"))active.put("shape",tool);point(lastX,lastY,e.getPressure(index));}catch(JSONException ex){active=null;}
            }else if(action==MotionEvent.ACTION_MOVE&&pointer!=-1){int i=e.findPointerIndex(pointer);if(i>=0){float x=e.getX(i),y=e.getY(i);
                if(tool.equals("pan")){offsetX+=x-lastX;offsetY+=y-lastY;lastX=x;lastY=y;}
                else if(tool.equals("select")){if(moving){RectF b=bounds();float dx=(x-lastX)/b.width(),dy=(y-lastY)/b.height();for(int selectedIndex:selected)try{JSONArray ps=strokes.getJSONObject(selectedIndex).getJSONArray("points");for(int p=0;p<ps.length();p++){JSONArray z=ps.getJSONArray(p);z.put(0,z.getDouble(0)+dx);z.put(1,z.getDouble(1)+dy);}}catch(JSONException ex){error(ex);}selection.offset(x-lastX,y-lastY);lastX=x;lastY=y;}else{selection.right=x;selection.bottom=y;}}
                else if(active!=null){for(int h=0;h<e.getHistorySize();h++)point(e.getHistoricalX(i,h),e.getHistoricalY(i,h),e.getHistoricalPressure(i,h));point(x,y,e.getPressure(i));}else erase(x,y);}}
            else if(action==MotionEvent.ACTION_CANCEL){active=null;pointer=-1;restore(history.cancel(strokes.toString()));}
            else if((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)&&e.getPointerId(index)==pointer){if(tool.equals("select")&&!moving){selection.sort();RectF b=bounds();for(int i=0;i<strokes.length();i++){JSONArray ps=strokes.optJSONObject(i).optJSONArray("points");for(int j=0;j<ps.length();j++){JSONArray p=ps.optJSONArray(j);if(selection.contains(b.left+(float)p.optDouble(0)*b.width(),b.top+(float)p.optDouble(1)*b.height())){selected.add(i);break;}}}message(selected.size()+" trazos seleccionados; arrastra dentro del marco para moverlos");}commit();pointer=-1;performClick();save();}
            invalidate();return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        void erase(float x,float y){RectF b=bounds();for(int i=strokes.length()-1;i>=0;i--){JSONObject original=strokes.optJSONObject(i);JSONArray pts=original.optJSONArray("points");boolean hit=false;for(int j=0;j<pts.length();j++){JSONArray p=pts.optJSONArray(j);if(Math.hypot(b.left+p.optDouble(0)*b.width()-x,b.top+p.optDouble(1)*b.height()-y)<24){hit=true;break;}}if(!hit)continue;
            strokes.remove(i);if(tool.equals("area")&&original.optString("shape").isEmpty())try{JSONArray segment=new JSONArray();for(int j=0;j<=pts.length();j++){JSONArray p=j==pts.length()?null:pts.optJSONArray(j);boolean removed=p==null||Math.hypot(b.left+p.optDouble(0)*b.width()-x,b.top+p.optDouble(1)*b.height()-y)<24;if(removed){if(segment.length()>0){JSONObject piece=new JSONObject(original.toString());piece.put("points",segment);strokes.put(piece);segment=new JSONArray();}}else segment.put(p);}}catch(JSONException e){error(e);}}
            invalidate();}
    }
}
