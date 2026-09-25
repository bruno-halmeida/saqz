-- Canal da janela de presença das 24 h (VUL-263). Migration própria: o valor novo do enum só pode
-- ser usado depois do commit desta (mesmo motivo da V78).
ALTER TYPE group_message_channel ADD VALUE 'ATTENDANCE_WINDOW';
