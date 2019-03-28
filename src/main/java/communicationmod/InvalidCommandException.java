package communicationmod;

public class InvalidCommandException extends Exception {

    private String[] command;
    private InvalidCommandFormat format;
    private String message = "";

    public InvalidCommandException(String[] command, InvalidCommandFormat format, String message) {
        super();
        this.command = command;
        this.format = format;
        this.message = message;
    }

    public InvalidCommandException(String[] command, InvalidCommandFormat format) {
        super();
        this.command = command;
        this.format = format;
    }

    public InvalidCommandException(String message) {
        super();
        this.message = message;
        this.format = InvalidCommandFormat.SIMPLE;
        this.command = new String[1];
        this.command[0] = "";
    }

    public String getMessage() {
        String wholeCommand = String.join(" ", this.command);
        switch (this.format) {
            case OUT_OF_BOUNDS:
                return String.format("Index %s out of bounds in command \"%s\"", this.message, wholeCommand);
            case MISSING_ARGUMENT:
                return String.format("Argument missing in command \"%s\".%s", wholeCommand, this.message);
            case INVALID_ARGUMENT:
                return String.format("Invalid argument %s in command \"%s\".", this.message, wholeCommand);
            default:
                return this.message;
        }
    }

    public enum InvalidCommandFormat {
        OUT_OF_BOUNDS,
        MISSING_ARGUMENT,
        INVALID_ARGUMENT,
        SIMPLE
    }

}
