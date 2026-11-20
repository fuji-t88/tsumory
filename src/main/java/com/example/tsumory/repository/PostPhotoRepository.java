package com.example.tsumory.repository;

import com.example.tsumory.domain.PostPhoto;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** つぶやきに添付した写真。主キーはつぶやきの ID。 */
public interface PostPhotoRepository extends JpaRepository<PostPhoto, Long> {

    /** そのユーザーのつぶやきの写真。ほかのユーザーのつぶやき・写真のないつぶやきなら空（2 つは区別しない）。 */
    @Query("""
            SELECT ph FROM PostPhoto ph
            WHERE ph.postId = :postId AND ph.post.user.id = :userId
            """)
    Optional<PostPhoto> findByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);

    /** 渡したつぶやきのうち、写真のあるつぶやきの ID。写真のデータは読まない（一覧に写真の有無を出すため）。 */
    @Query("""
            SELECT ph.postId FROM PostPhoto ph
            WHERE ph.postId IN :postIds
            """)
    List<Long> findPostIdsWithPhoto(@Param("postIds") Collection<Long> postIds);
}
