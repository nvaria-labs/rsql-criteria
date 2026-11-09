package visitors;

import ch.nvaria.rsql.RsqlHidden;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class HiddenAudited {

    @RsqlHidden
    protected String createdBy;

    protected String source;
}
