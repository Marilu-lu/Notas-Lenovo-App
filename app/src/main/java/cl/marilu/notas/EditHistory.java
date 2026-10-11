package cl.marilu.notas;

import java.util.ArrayDeque;

/** Bounded snapshots: every gesture is one reversible transaction. */
public final class EditHistory {
    private final ArrayDeque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private String before;
    private void trim(){long size=0;for(String s:undo)size+=s.length();for(String s:redo)size+=s.length();while(undo.size()>1&&(undo.size()>60||size>8_000_000)){size-=undo.removeFirst().length();}}
    public void begin(String value) { if(before == null) before=value; }
    public void finish(String value) {
        if(before != null && !before.equals(value)) {
            undo.addLast(before); redo.clear();trim();
        }
        before=null;
    }
    public String cancel(String value) { String result=before==null?value:before; before=null; return result; }
    public String undo(String value) { finish(value); if(undo.isEmpty())return value; redo.addLast(value);String result=undo.removeLast();trim();return result; }
    public String redo(String value) { if(redo.isEmpty())return value;undo.addLast(value);String result=redo.removeLast();trim();return result; }
    public void clear() {undo.clear();redo.clear();before=null;}
}
