CREATE INDEX userid_createdat_linkid on links(user_id, created_at DESC, id DESC );
DROP INDEX links_user_id_idx;