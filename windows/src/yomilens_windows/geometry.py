from dataclasses import dataclass


@dataclass(frozen=True)
class Region:
    left: int
    top: int
    width: int
    height: int

    def capture_dict(self):
        return dict(left=self.left, top=self.top, width=self.width, height=self.height)


def logical_to_physical(rect, logical_origin, physical_origin, scale) -> Region:
    x, y, w, h = rect
    left = physical_origin[0] + round((x - logical_origin[0]) * scale)
    top = physical_origin[1] + round((y - logical_origin[1]) * scale)
    return Region(left, top, max(1, round(w * scale)), max(1, round(h * scale)))


def physical_box_to_local(box, scale):
    xs, ys = zip(*box)
    return min(xs)/scale, min(ys)/scale, (max(xs)-min(xs))/scale, (max(ys)-min(ys))/scale
