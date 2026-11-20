package com.example.tsumory.service;

import java.util.Objects;

/** つぶやきを編集するときの、写真の変え方（phase3-spec.md の「6. 処理の詳細 > つぶやきの編集」）。 */
public sealed interface PhotoChange {

    /** 写真を変えない（写真がなければ、ないまま）。 */
    PhotoChange KEEP = new Keep();

    /** 写真を外す（本文は残す。PH-REQ-06）。 */
    PhotoChange REMOVE = new Remove();

    /** 写真を変えない。 */
    record Keep() implements PhotoChange {}

    /** 写真を外す。写真がなければ何もしない。 */
    record Remove() implements PhotoChange {}

    /** 写真を差し替える。写真がなければ添付する（PH-REQ-05）。 */
    record Replace(PhotoUpload photo) implements PhotoChange {
        public Replace {
            Objects.requireNonNull(photo, "photo");
        }
    }
}
