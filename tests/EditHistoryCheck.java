import cl.marilu.notas.EditHistory;

public class EditHistoryCheck {
    static void equal(String wanted,String actual) { if(!wanted.equals(actual))throw new AssertionError(wanted+" != "+actual); }
    public static void main(String[] args) {
        EditHistory h=new EditHistory();
        h.begin("[]");h.finish("[pen]");
        h.begin("[pen]");h.finish("[]");
        equal("[pen]",h.undo("[]")); // Erased ink must return.
        equal("[]",h.redo("[pen]"));
        equal("[pen]",h.undo("[]"));
        h.begin("[pen]");h.finish("[pen,blue]");
        equal("[pen,blue]",h.redo("[pen,blue]")); // New edit invalidates redo.
        h.begin("[pen,blue]");equal("[pen,blue]",h.cancel("[pen,blue,partial]"));
        h.clear();equal("[]",h.undo("[]"));
        System.out.println("History checks passed: erase, redo, branching, cancel and clear.");
    }
}
