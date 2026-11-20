package com.example.tsumory.repository;

import com.example.tsumory.domain.PostPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

/** つぶやきに添付した写真。主キーはつぶやきの ID。 */
public interface PostPhotoRepository extends JpaRepository<PostPhoto, Long> {}
