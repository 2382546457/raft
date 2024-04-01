package com.alipay.sofa.jraft;

import com.alipay.sofa.jraft.util.Copiable;
import com.alipay.sofa.jraft.error.RaftError;

import java.util.Objects;

/**
 * @author : 小何
 * @Description :
 * @date : 2024-03-31 14:46
 */
public class Status implements Copiable<Status> {
    private static class State {
        int code;
        String message;

        public State(int code, String message) {
            this.code = code;
            this.message = message;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (object == null || getClass() != object.getClass()) return false;
            State state = (State) object;
            return code == state.code && Objects.equals(message, state.message);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + this.code;
            result = prime * result + (this.message == null ? 0 : this.message.hashCode());
            return result;
        }
    }

    private State state;

    public Status() {
    }
    public Status(RaftError raftError, String fmt, Object... args) {
        this.state = new State(raftError.getNumber(), String.format(fmt, args));
    }
    public Status(Status s) {
        if (s.state != null) {
            this.state = new State(s.state.code, s.state.message);
        } else {
            this.state = null;
        }
    }

    public Status(int code, String fmt, Object... args) {
        this.state = new State(code, String.format(fmt, args));
    }

    public Status(int code, String errorMsg) {
        this.state = new State(code, errorMsg);
    }
    public static Status OK() {
        return new Status();
    }
    public void reset() {
        this.state = null;
    }
    public boolean isOk() {
        return this.state == null || this.state.code == 0;
    }
    public void setCode(int code) {
        if (this.state == null) {
            this.state = new State(code, null);
        } else {
            this.state.code = code;
        }
    }
    public int getCode() {
        return this.state == null ? 0 : this.state.code;
    }
    public void setErrorMsg(String errMsg) {
        if (this.state == null) {
            this.state = new State(0, errMsg);
        } else {
            this.state.message = errMsg;
        }
    }
    public void setError(int code, String fmt, Object... args) {
        this.state = new State(code, String.format(String.valueOf(fmt), args));
    }
    public void setError(RaftError error, String fmt, Object... args) {
        this.state = new State(error.getNumber(), String.format(String.valueOf(fmt), args));
    }
    public RaftError getRaftError() {
        return this.state == null ? RaftError.SUCCESS : RaftError.forNumber(this.state.code);
    }

    @Override
    public Status copy() {
        return new Status(this.getCode(), this.getErrorMsg());
    }

    /**
     * Get the error msg.
     */
    public String getErrorMsg() {
        return this.state == null ? null : this.state.message;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (this.state == null ? 0 : this.state.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Status other = (Status) obj;
        if (this.state == null) {
            return other.state == null;
        } else {
            return this.state.equals(other.state);
        }
    }
    @Override
    public String toString() {
        if (isOk()) {
            return "Status[OK]";
        } else {
            return "Status[" + RaftError.describeCode(this.state.code) + "<" + this.state.code + ">: " + this.state.message
                    + "]";
        }
    }
}
