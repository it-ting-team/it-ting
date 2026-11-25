ALTER TABLE chat_room
    ADD CONSTRAINT fk_chat_room_meetup FOREIGN KEY (meetup_id) REFERENCES meetup (id);
